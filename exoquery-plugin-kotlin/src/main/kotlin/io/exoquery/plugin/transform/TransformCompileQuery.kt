package io.exoquery.plugin.transform

import com.github.vertical_blank.sqlformatter.SqlFormatter
import io.decomat.*
import io.exoquery.Phase
import io.exoquery.PostgresDialect
import io.exoquery.ActionReturningKind
import io.exoquery.SqlAction
import io.exoquery.SqlBatchAction
import io.exoquery.SqlQuery
import io.exoquery.annotation.ExoBuildFunctionLabel
import io.exoquery.parseError
import io.exoquery.plugin.funName
import io.exoquery.plugin.hasAnnotation
import io.exoquery.plugin.isClass
import io.exoquery.plugin.location
import io.exoquery.plugin.makeRunFunction
import io.exoquery.plugin.ownerFunction
import io.exoquery.plugin.safeName
import io.exoquery.plugin.show
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.Lifter
import io.exoquery.plugin.trees.SqlActionExpr
import io.exoquery.plugin.trees.SqlBatchActionExpr
import io.exoquery.plugin.trees.SqlQueryExpr
import io.exoquery.plugin.trees.simpleValueParams
import io.exoquery.plugin.zipArgsWithParamsOrFail
import io.exoquery.sql.Renderer
import io.exoquery.sql.SqlIdiom
import io.exoquery.sql.Token
import io.exoquery.util.TraceConfig
import io.exoquery.xr.XR
import io.exoquery.xr.encode
import io.exoquery.xr.toActionKind
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNullableString
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.utils.zipIfSizesAreEqual
import kotlin.reflect.full.isSuperclassOf
import kotlin.time.measureTimedValue


class TransformCompileQuery(val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {

  private fun isNamedBuild(name: String) = name == "build" || name == "buildPretty"

  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun matches(expr: IrCall): Boolean =
    (expr.dispatchReceiver?.type?.let { it.isClass<SqlQuery<*>>() || it.isClass<SqlAction<*, *>>() || it.isClass<SqlBatchAction<*, *, *>>() } ?: false) && isNamedBuild(expr.symbol.safeName)

  // TODO need lots of cleanup and separation of concerns here
  // TODO propagate labels into the trace logger
  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun transform(expr: IrCall): IrExpression {

    fun extractDialectConstructor(dialectType: IrType) = run {
      // get the empty-args constructor from the dialect-type, if it doesn''t we need to know about it. If it does exist we'll use it if we need to use a runtime query
      val dialectCls = (dialectType.classOrNull ?: parseError("The dialect type must be a class but instead it was: ${dialectType.dumpKotlinLike()}", expr))
      val construct = dialectCls.constructors.find { it.owner.valueParameters.size == 1 } ?: parseError("The dialect type must have 1-arg constructor that takes a trace-types argument but it did not", expr)

      // see if we can get the dialect instance at compile-time if we can't then create an error
      // TODO if the user has marked the file with a special annotation then allow the runtime `construct` to create the dialect if the class.forName fails otherwise immediately throw an error here
      val clsPackageName = dialectCls.owner.kotlinFqName.asString() ?: parseError("The dialect class ${dialectType.dumpKotlinLike()} must have a package name but it did not", expr)
      construct to clsPackageName
    }

    val transfomerScope = symbolSet

    data class BuildFunctionArgs(val queryLabel: String?)

    // Making args extraction somewhat modular so can add more args to the build function in the future
    fun extractArgs() = run {
      val partsWithParams = expr.zipArgsWithParamsOrFail()
      val queryLabel =
        partsWithParams
          .find { (param, value) -> param.hasAnnotation<ExoBuildFunctionLabel>() }?.second
          // If the of the parameter is null then it must not be present and we don't care. If it does exist make sure it's an IrConst
          ?.let { it as? IrConst ?: parseError("A query-label must be a constant compile-time string but found: ${it.dumpKotlinLike()}", it) }
          ?.let { it.value.toString() }

      BuildFunctionArgs(queryLabel)
    }

    // recurse down into the expression in order to make it into an Uprootable if needed
    return expr.match(
      case(Ir.Call.FunctionMemN[Is(), Is.invoke { isNamedBuild(it) }, Is(/*This is the dialect*/)]).thenThis { sqlQueryExprRaw, _ ->
        val isPretty = expr.symbol.safeName == "buildPretty"
        val parsedArgs = extractArgs()

        val compileLocation = expr.location(currentFile.fileEntry)
        val fileLabel = (parsedArgs.queryLabel?.let {it + " - "} ?: "") + "file:${compileLocation.show()}"
        val dialectType = this.typeArguments.first() ?: parseError("Need to pass a constructable dialect to the build method but no argument was provided", expr)
        val (traceConfig, writeSource) = ComputeEngineTracing.invoke(fileLabel, dialectType)
        val (construct, clsPackageName) = extractDialectConstructor(dialectType)

        // If sqlQueryExprRaw is an Uprootable then we need to do superTransformer.visitCall on that which typically will happen if you call .build on a capture directly
        // e.g. in `capture { Table<Person>() }.build<PostgresDialect>()` sqlQueryExprRaw will be the IrCall `capture { Table<Person>() }` on which we need to call the superTransformer.visitCall.
        // In the case that sqlQueryExprRaw is some kind of expression e.g.
        // val cap = capture { Table<Person>() }; cap.build<PostgresDialect>() then the uprootable has already been created by the variable declaration
        // and is something like `val cap = SqlQuery(xr=...)`. That means that we need to do a TransformProjectCapture on the `cap` varaible that is being called
        // which is invoked by the `superTransformer.visitExpression`.
        // In both of these cases superTransformer.recurse delegates-out to the correct transformer based on the type-match of the expression.
        val sqlExpr = superTransformer.recurse(sqlQueryExprRaw)
        val containerType = expr.dispatchReceiver?.type ?: parseError("Invalid container type ${expr.dispatchReceiver?.type?.dumpKotlinLike()}. (Reciver ${expr.dispatchReceiver?.dumpKotlinLike()})", expr)
        val dialect = ConstructCompiletimeDialect.of(clsPackageName, traceConfig)

        fun Token.renderQueryString(pretty: Boolean, xr: XR) = run {
          val queryStringRaw =
            try {
              this.renderWith(Renderer(false, false))
            } catch (e: Exception) {
              parseError("The query could not be rendered: ${e.message}\n------------------\n${xr.showRaw()}", expr)
            }
          if (isPretty)
            SqlFormatter.format(queryStringRaw)
          else
            queryStringRaw
        }

        when (ContainerType.determine(containerType)) {
          is ContainerType.Query -> {
            sqlExpr.match(
              case(SqlQueryExpr.Uprootable[Is()]).then { uprootable ->
                val xr = uprootable.unpackOrErrorXR().successOrParseError(sqlExpr)

                val (queryAndToken, compileTime) = measureTimedValue {
                  try {
                    dialect.processQuery(xr)
                  } finally {
                    // close file writing if it was happening
                    writeSource?.close()
                  }
                }
                val (queryTokenized, query) = queryAndToken

                // Can include the sql-formatting library here since the compiler is always on the JVM!
                val queryString = queryTokenized.renderQueryString(isPretty, xr)
                accum.addQuery(PrintableQuery(queryString, compileLocation, parsedArgs.queryLabel))

                val msgAdd = parsedArgs.queryLabel?.let { " ($it)" } ?: ""
                this@Scope.logger.report("Compiled query in ${compileTime.inWholeMilliseconds}ms${msgAdd}: ${queryString}", expr)
                SqlCompiledQueryExpr(sqlExpr, queryString, queryTokenized, false, parsedArgs.queryLabel, Phase.CompileTime, uprootable.packedXR, query.encode()).plant()
              }
            ) ?: run {
              logger.warn("The query could not be transformed at compile-time", expr.location())
              callBuildRuntime(construct, traceConfig, parsedArgs.queryLabel, isPretty, sqlExpr)
            }
          }
          is ContainerType.Action -> {
            sqlExpr.match(
              case(SqlActionExpr.Uprootable[Is()]).then { uprootable ->
                val xr = uprootable.unpackOrErrorXR().successOrParseError(sqlExpr)
                val (queryTokenized, compileTime) = measureTimedValue {
                  try {
                    dialect.processAction(xr)
                  } finally {
                    // close file writing if it was happening
                    writeSource?.close()
                  }
                }

                // Can include the sql-formatting library here since the compiler is always on the JVM!
                val queryString = queryTokenized.renderQueryString(isPretty, xr)
                val actionKind = xr.toActionKind()
                val actionReturningKind = ActionReturningKind.fromActionXR(xr)
                accum.addQuery(PrintableQuery(queryString, compileLocation, parsedArgs.queryLabel))

                val msgAdd = parsedArgs.queryLabel?.let { " ($it)" } ?: ""
                logger.report("Compiled action in ${compileTime.inWholeMilliseconds}ms: ${queryString}", expr)

                val sqlActionTmpVar = builder.scope.createTmpVariable(sqlExpr)
                val output = SqlCompiledActionExpr(builder.irGet(sqlActionTmpVar), queryString, queryTokenized, actionKind, actionReturningKind, parsedArgs.queryLabel, Phase.CompileTime, uprootable.packedXR).plant()
                // IMPORTANT notes inside of makeRunFunction as to why this is used here
                makeRunFunction(listOf(sqlActionTmpVar), output)
              }
            ) ?: run {
              //logger.warn("The action could not be transformed at compile-time", expr.location())
              logger.warn("The action could not be transformed at compile-time", expr)
              callBuildRuntime(construct, traceConfig, parsedArgs.queryLabel, isPretty, sqlExpr)
            }
          }
          is ContainerType.BatchAction -> {
            //logger.error("---------------- HERE:\n${sqlExpr.dumpKotlinLike()}")
            sqlExpr.match(
              case(SqlBatchActionExpr.Uprootable[Is()]).then { uprootable ->
                val xr = uprootable.unpackOrErrorXR().successOrParseError(sqlExpr)
                val (queryTokenized, compileTime) = measureTimedValue {
                  try {
                    dialect.processAction(xr.action)
                  } finally {
                    // close file writing if it was happening
                    writeSource?.close()
                  }
                }

                // Can include the sql-formatting library here since the compiler is always on the JVM!
                val queryString = queryTokenized.renderQueryString(isPretty, xr)
                val actionKind = xr.action.toActionKind()
                val actionReturningKind = ActionReturningKind.fromActionXR(xr.action)
                accum.addQuery(PrintableQuery(queryString, compileLocation, parsedArgs.queryLabel))

                val msgAdd = parsedArgs.queryLabel?.let { " ($it)" } ?: ""
                logger.report("Compiled batch-action in ${compileTime.inWholeMilliseconds}ms: ${queryString}", expr)

                SqlCompiledBatchActionExpr(sqlExpr, queryString, queryTokenized, actionKind, actionReturningKind, parsedArgs.queryLabel, Phase.CompileTime, uprootable.packedXR).plant()
              }
            ) ?: run {
              logger.warn("The batch-action could not be transformed at compile-time", expr.location())
              with (Lifter(this@Builder)) {
                val labelExpr = if (parsedArgs.queryLabel != null) parsedArgs.queryLabel.lift() else irBuilder.irNull()
                // we still know it's x.build or x.buildPretty so just use that (for now ignore formatting if it is at runtime)
                val dialect = buildRuntimeDialect(construct, traceConfig)
                sqlExpr.callDispatch("buildRuntime")(dialect, labelExpr, isPretty.lift())
              }
            }
          }
        }
      }
    ) ?: run {
      expr
    }
  }

  sealed interface ContainerType {
    data object Query: ContainerType
    data object Action: ContainerType
    data object BatchAction: ContainerType
    companion object {
      context(CX.Scope)
      fun determine(type: IrType) =
        when {
          type.isClass<SqlQuery<*>>() -> Query
          type.isClass<SqlAction<*, *>>() -> Action
          type.isClass<SqlBatchAction<*, *, *>>() -> BatchAction
          else -> parseError("The type ${type.dumpKotlinLike()} must be a SqlQuery or SqlAction but it was not")
        }
    }
  }

  context(CX.Scope, CX.Builder)
  fun callBuildRuntime(construct: IrConstructorSymbol, traceConfig: TraceConfig, queryLabel: String?, isPretty: Boolean, sqlQueryExpr: IrExpression): IrExpression = run {
    with (Lifter(this@Builder)) {
      val labelExpr = if (queryLabel != null) queryLabel.lift() else irBuilder.irNull()
      // we still know it's x.build or x.buildPretty so just use that (for now ignore formatting if it is at runtime)
      val dialect = buildRuntimeDialect(construct, traceConfig)
      sqlQueryExpr.callDispatch("buildRuntime")(dialect, labelExpr, isPretty.lift())
    }
  }

  context(CX.Scope, CX.Builder)
  fun buildRuntimeDialect(construct: IrConstructorSymbol, traceConfig: TraceConfig) =
    builder.irCall(construct).apply {
      with (makeLifter()) {
        putValueArgument(0, traceConfig.lift(options.projectDir))
      }
    }
}

object ConstructCompiletimeDialect {
  context(CX.Scope, CX.Builder)
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
