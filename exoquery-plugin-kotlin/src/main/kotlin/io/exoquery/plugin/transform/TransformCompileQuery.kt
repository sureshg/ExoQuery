package io.exoquery.plugin.transform

import com.github.vertical_blank.sqlformatter.SqlFormatter
import io.decomat.*
import io.exoquery.PostgresDialect
import io.exoquery.SqlCompiledQuery
import io.exoquery.SqlExpression
import io.exoquery.SqlQuery
import io.exoquery.annotation.TracesEnabled
import io.exoquery.parseError
import io.exoquery.plugin.getAnnotation
import io.exoquery.plugin.hasAnnotation
import io.exoquery.plugin.isClass
import io.exoquery.plugin.location
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.FilePrintOutputSource
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.safeName
import io.exoquery.plugin.source
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.Lifter
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.SqlExpressionExpr
import io.exoquery.plugin.trees.SqlQueryExpr
import io.exoquery.plugin.trees.simpleTypeArgs
import io.exoquery.plugin.varargValues
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import java.awt.print.Printable
import kotlin.time.measureTime
import kotlin.time.measureTimedValue


class TransformCompileQuery(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {

  private fun isNamedBuild(name: String) = name == "build" || name == "buildPretty"

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expr: IrCall): Boolean =
    (expr.dispatchReceiver?.type?.isClass<SqlQuery<*>>() ?: false) && isNamedBuild(expr.symbol.safeName)

  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(expr: IrCall): IrExpression {
    // recurse down into the expression in order to make it into an Uprootable if needed
    return expr.match(
      case(Ir.Call.FunctionMemN[Is(), Is.invoke { isNamedBuild(it) }, Is(/*TODO this is the dialect*/)]).then { sqlQueryExprRaw, args ->
        val isPretty = expr.symbol.safeName == "buildPretty"

        // get annotations
        val traceTypesClsRef = currentFileRaw.getAnnotation<TracesEnabled>()?.valueArguments?.firstOrNull()?.varargValues() ?: emptyList()
        val traceTypesNames =
          traceTypesClsRef
            .map { ref -> (ref as? IrClassReference) ?: parseError("Invalid Trace Type: ${ref.source() ?: ref.dumpKotlinLike()} was not a class-reference:\n${ref.dumpSimple()}") }
            .mapNotNull { ref ->
              // it's TracesEnabled(KClass<TraceType.Normalizations>, etc...) so we need to take 1st argument from the type
              val shortName = ref.type.simpleTypeArgs.first().classFqName?.shortName()?.asString()
              shortName?.let { TraceType.fromClassStr(it) }
            }

        val writeSource =
          if (traceTypesNames.isNotEmpty())
            FilePrintOutputSource.open(options)
          else
            null
        val traceConfig = TraceConfig.empty.copy(traceTypesNames, writeSource ?: Tracer.OutputSource.None)

        warn("-------------- TracesEnabled Annotation (${writeSource}) ---------------\n" + traceTypesNames.joinToString(","), currentFileRaw.location())

        val dialect = args[0]
        val label =
          if (args.size > 1) {
            (args[1] as? IrConst)?.let { constVal -> constVal.value.toString() }
              ?: parseError("A query-label must be a constant compile-time string but found: ${args[1].dumpKotlinLike()}", args[1])
          } else {
            null
          }

        val sqlQueryExpr = superTransformer.visitExpression(sqlQueryExprRaw)
        sqlQueryExpr.match(
          // .thenIf { _ -> false }
          case(SqlQueryExpr.Uprootable[Is()]).then { uprootable ->
            val xr = uprootable.xr // deserialize the XR, TODO need to handle deserialization failures here
            val dialect = PostgresDialect(traceConfig) // TODO compiler-arg or file-annotation to add a trace config to trace phases during compile-time?
            val (queryStringRaw, compileTime) = measureTimedValue {
              dialect.translate(xr) // TODO catch any potential errors coming from the query compiler
            }

            // Can include the sql-formatting library here since the compiler is always on the JVM!
            val queryString =
              if (isPretty)
                SqlFormatter.format(queryStringRaw)
              else
                queryStringRaw

            // close file writing if it was happening
            writeSource?.close()

            ctx.transformerScope.addQuery(PrintableQuery(queryString, expr.location(ctx.currentFile.fileEntry), label))

            report("Compiled query in ${compileTime.inWholeMilliseconds}ms: ${queryString}", expr)

            val lifter = Lifter(ctx)
            // Gete the type T of the SqlQuery<T> that .build is called on
            val queryOutputType = sqlQueryExpr.type.simpleTypeArgs[0]


            with (lifter) {
              val labelExpr = if (label != null) label.lift() else irBuilder.irNull()
              makeWithTypes<SqlCompiledQuery<*>>(listOf(queryOutputType), listOf(queryString.lift(), labelExpr))
            }
          }
        ) ?: run {
          warn("The query could not be transformed at compile-time", expr.location(ctx.currentFile.fileEntry))
          with (Lifter(ctx)) {
            val labelExpr = if (label != null) label.lift() else irBuilder.irNull()
            // we still know it's x.build or x.buildPretty so just use that (for now ignore formatting if it is at runtime)

            // TODO find a way to lift TraceTypes so can pass file:TraceTypes to the runtime configs
            expr.dispatchReceiver!!.callDispatch("buildRuntime")(dialect, labelExpr, isPretty.lift())
          }
        }
      }
    ) ?: run {
      expr
    }
  }
}
