package io.exoquery.plugin.transform

import com.github.vertical_blank.sqlformatter.SqlFormatter
import io.decomat.*
import io.exoquery.PostgresDialect
import io.exoquery.SqlCompiledQuery
import io.exoquery.SqlQuery
import io.exoquery.parseError
import io.exoquery.plugin.findMethodOrFail
import io.exoquery.plugin.isClass
import io.exoquery.plugin.location
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.safeName
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.plugin.transform.ConstructCompiletimeDialect
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.Lifter
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.SqlQueryExpr
import io.exoquery.plugin.trees.simpleTypeArgs
import io.exoquery.sql.SqlIdiom
import io.exoquery.sql.token
import io.exoquery.util.TraceConfig
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.types.model.typeConstructor
import kotlin.reflect.full.isSuperclassOf
import kotlin.time.measureTimedValue


class TransformCompileQuery(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {

  private fun isNamedBuild(name: String) = name == "build" || name == "buildPretty"

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expr: IrCall): Boolean =
    (expr.dispatchReceiver?.type?.isClass<SqlQuery<*>>() ?: false) && isNamedBuild(expr.symbol.safeName)

  // TODO need lots of cleanup and separation of concerns here
  // TODO propagate labels into the trace logger
  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(expr: IrCall): IrExpression {

    fun extractDialectConstructor(dialectType: IrType) = run {
      // get the empty-args constructor from the dialect-type, if it doesn''t we need to know about it. If it does exist we'll use it if we need to use a runtime query
      val dialectCls = (dialectType.classOrNull ?: parseError("The dialect type must be a class but instead it was: ${dialectType.dumpKotlinLike()}", expr))
      val construct = dialectCls.constructors.find { it.owner.valueParameters.size == 1 } ?: parseError("The dialect type must have 1-arg constructor that takes a trace-types argument but it did not", expr)

      // see if we can get the dialect instance at compile-time if we can't then create an error
      // TODO if the user has marked the file with a special annotation then allow the runtime `construct` to create the dialect if the class.forName fails otherwise immediately throw an error here
      val clsPackageName = dialectCls.owner.kotlinFqName.asString() ?: parseError("The dialect class ${dialectType.dumpKotlinLike()} must have a package name but it did not", expr)
      construct to clsPackageName
    }

    fun buildRuntimeDialect(construct: IrConstructorSymbol, traceConfig: TraceConfig) =
      ctx.builder.irCall(construct).apply {
        with (ctx.makeLifter()) {
          putValueArgument(0, traceConfig.lift(ctx.options.projectDir))
        }
      }

    // recurse down into the expression in order to make it into an Uprootable if needed
    return expr.match(
      case(Ir.Call.FunctionMemN[Is(), Is.invoke { isNamedBuild(it) }, Is(/*TODO this is the dialect*/)]).thenThis { sqlQueryExprRaw, args ->
        val isPretty = expr.symbol.safeName == "buildPretty"

        val (traceConfig, writeSource) = ComputeEngineTracing.invoke()
        val dialectType = this.typeArguments.first() ?: parseError("Need to pass a constructable dialect to the build method but no argument was provided", expr)
        val (construct, clsPackageName) = extractDialectConstructor(dialectType)

        val label =
          if (args.size > 0) {
            (args.first() as? IrConst)?.let { constVal -> constVal.value.toString() }
              ?: parseError("A query-label must be a constant compile-time string but found: ${args[1].dumpKotlinLike()}", args[1])
          } else {
            null
          }

        val sqlQueryExpr = superTransformer.visitExpression(sqlQueryExprRaw)
        sqlQueryExpr.match(
          // .thenIf { _ -> false }
          case(SqlQueryExpr.Uprootable[Is()]).then { uprootable ->
            val xr = uprootable.xr // deserialize the XR, TODO need to handle deserialization failures here
            val dialect = ConstructCompiletimeDialect.of(clsPackageName, traceConfig) // TODO compiler-arg or file-annotation to add a trace config to trace phases during compile-time?

            val (queryTokenized, compileTime) = measureTimedValue {
              try {
                dialect.processQuery(xr) // TODO catch any potential errors coming from the query compiler
              } finally {
                // close file writing if it was happening
                writeSource?.close()
              }
            }

            // Can include the sql-formatting library here since the compiler is always on the JVM!
            val queryStringRaw =
              try {
                queryTokenized.build()
              } catch (e: Exception) {
                parseError("The query could not be compiled: ${e.message}\n------------------\n${xr.showRaw()}", expr)
              }

            val queryString =
              if (isPretty)
                SqlFormatter.format(queryStringRaw)
              else
                queryStringRaw



            ctx.transformerScope.addQuery(PrintableQuery(queryString, expr.location(ctx.currentFile.fileEntry), label))

            report("Compiled query in ${compileTime.inWholeMilliseconds}ms: ${queryString}", expr)

            val lifter = Lifter(ctx)
            // Gete the type T of the SqlQuery<T> that .build is called on
            val queryOutputType = sqlQueryExpr.type.simpleTypeArgs[0]

            // i.e. this is the SqlQuery.params call
            val callParamsFromSqlQuery = sqlQueryExpr.callDispatch("params").invoke()

            // TODO also need to add filtered ParamSet (i.e. making sure we have everything we need from the Token params, filtered and in the right order) to the SqlCompiledQuery
            with (lifter) {
              val labelExpr = if (label != null) label.lift() else irBuilder.irNull()
              makeWithTypes<SqlCompiledQuery<*>>(
                listOf(queryOutputType),
                listOf(
                  queryString.lift(), // value
                  queryTokenized.token.lift(callParamsFromSqlQuery), // token
                  // If there are no ParamMulti values then we know that we can use the original query built with the .build function.
                  // Now we can't check what the values in ParamSet are but we have this value in the TagForParam.paramType field
                  // which shows us if the Param is a ParamSingle or ParamMulti. We need to check that in the AST in order to know that this
                  // value is supposed to be.
                  irBuilder.irBoolean(false), // needsTokenization (todo need to determine this from the tokenized value i.e. only `true` if there are no ParamMulti values)
                  labelExpr
                )
              )
            }
          }
        ) ?: run {
          warn("The query could not be transformed at compile-time", expr.location(ctx.currentFile.fileEntry))
          with (Lifter(ctx)) {
            val labelExpr = if (label != null) label.lift() else irBuilder.irNull()
            // we still know it's x.build or x.buildPretty so just use that (for now ignore formatting if it is at runtime)
            val dialect = buildRuntimeDialect(construct, traceConfig)
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

object ConstructCompiletimeDialect {
  context(LocationContext, BuilderContext, CompileLogger)
  fun of(fullPath: String, traceConfig: TraceConfig) =
    when {
      "io.exoquery.PostgresDialect" in fullPath -> PostgresDialect(traceConfig)
      else ->
        (Class.forName(fullPath) ?: parseError("Could not find the dialect class: ${fullPath}"))
          .constructors.find {
            it.parameters.size == 1 && it.parameters.first().type.kotlin.isSuperclassOf(TraceConfig::class)
          }?.let { it.newInstance(traceConfig) } as SqlIdiom
            ?: parseError("The dialect class ${fullPath} must have a 1-arg constructor that takes a trace-config but it did not")
    }
}
