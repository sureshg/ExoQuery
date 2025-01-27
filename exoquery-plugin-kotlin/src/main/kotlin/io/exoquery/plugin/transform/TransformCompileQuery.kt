package io.exoquery.plugin.transform

import io.decomat.*
import io.exoquery.PostgresDialect
import io.exoquery.SqlCompiledQuery
import io.exoquery.SqlExpression
import io.exoquery.SqlQuery
import io.exoquery.plugin.isClass
import io.exoquery.plugin.location
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.safeName
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.Lifter
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.SqlExpressionExpr
import io.exoquery.plugin.trees.SqlQueryExpr
import io.exoquery.plugin.trees.simpleTypeArgs
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.*
import kotlin.time.measureTime
import kotlin.time.measureTimedValue


class TransformCompileQuery(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expr: IrCall): Boolean =
    (expr.dispatchReceiver?.type?.isClass<SqlQuery<*>>() ?: false) && expr.symbol.safeName == "build"

  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(expr: IrCall): IrExpression {
    // recurse down into the expression in order to make it into an Uprootable if needed
    return expr.match(
      case(Ir.Call.FunctionMem1[Is(), Is("build"), Is(/*TODO this is the dialect*/)]).then { sqlQueryExprRaw, dialect ->
        val sqlQueryExpr = superTransformer.visitExpression(sqlQueryExprRaw)
        sqlQueryExpr.match(
          case(SqlQueryExpr.Uprootable[Is()]).then { uprootable ->
            val xr = uprootable.xr // deserialize the XR, TODO need to handle deserialization failures here
            val dialect = PostgresDialect() // TODO compiler-arg or file-annotation to add a trace config to trace phases during compile-time?
            val (queryString, compileTime) = measureTimedValue {
              dialect.translate(xr) // TODO catch any potential errors coming from the query compiler
            }
            ctx.transformerScope.addQuery(queryString, expr.location(ctx.currentFile.fileEntry))

            // TODO can include the sql-formatting library here since the compiler is always on the JVM!
            report("Compiled query in ${compileTime.inWholeMilliseconds}ms: ${queryString}", expr)

            val lifter = Lifter(ctx)
            // Gete the type T of the SqlQuery<T> that .build is called on
            val queryOutputType = sqlQueryExpr.type.simpleTypeArgs[0]
            with (lifter) {
              makeWithTypes<SqlCompiledQuery<*>>(listOf(queryOutputType), listOf(queryString.lift()))
            }
          }
        ) ?: run {
          warn("The query could not be transformed at compile-time", expr.location(ctx.currentFile.fileEntry))
          expr
        }
      }
    ) ?: run {
      expr
    }
  }
}