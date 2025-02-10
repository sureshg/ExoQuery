package io.exoquery.plugin.transform

import io.decomat.*
import io.exoquery.SqlQuery
import io.exoquery.annotation.CapturedFunction
import io.exoquery.parseError
import io.exoquery.plugin.funName
import io.exoquery.plugin.hasAnnotation
import io.exoquery.plugin.isClass
import io.exoquery.plugin.location
import io.exoquery.plugin.locationXR
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.Parser
import io.exoquery.plugin.trees.SqlQueryExpr
import io.exoquery.plugin.trees.of
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.statements
import kotlin.with


/**
 * A captured function in the form of:
 * ```
 * @CapturedFunction
 * fun nameIsX(people: SqlQuery<Person>, name: String): SqlQuery<Person> = capture { people.filter { p -> p.name == name } }
 *
 * // Is supposed to be transformed into a capture-instance with a lambda that looks like this:
 * fun nameIsX() =
 *   SqlQuery(XR.Function(args = Id(people), Id(name), body = XR.Filter(Id(people), BinaryOp(Prop(Id(p), "name"), "==", Id(name))))
 * ```
 * So effectively we take the parameters of the function and roll them up under a lambda that is then transformed into a XR instance.
 *
 *
 * Note however that if there are any params in this function they need to interpreted normally and the be passed in the SqlQuery instance
 * so that later in the TransformProjectCapture stage they can undergo the correct transformation.
 * For example say
 * ```
 * val name = ...
 * fun nameIsParam(people: SqlQuery<Person>): SqlQuery<Person> = capture { people.filter { p -> p.name == param(name) } }
 *
 * // This needs to be transformed into:
 * fun nameIsParam() =
 *   SqlQuery(XR.Function(
 *     args = Id(people), body = XR.Filter(Id(people), BinaryOp(Prop(Id(p), "name"), "==", Param(Tag(123))),
 *     params = listOf(Param(Tag(123), Id(name)))
 *   )
 * ```
 * The later in the TransformProjectCapture stage the caller of this function that looks roughly like this:
 * ```
 * nameIsParam(Table<Person>()).map { ...param(something)... }
 * // Needs to be transformed into the following (note that we drop the parameters being used in the call!)
 *
 * SqlQuery(XR.FunctionApply(nameIsParam(), listOf(Table<Person>()) ), nameIsParam().params ++ this.params /*i.e. param(something) */) ->
 *   SqlQuery(XR.FunctionApply(SqlQuery(XR.Function(args = Id(people), body = XR.Filter(Id(people), BinaryOp(Prop(Id(p), "name"), "==", Param(Tag(123))), params = nameIsParam().params ++ this.params)
 * ```
 *
 * If there are any runtimes in this function the function still needs to be captured in the fasion above but the runtimes need to be maintained.
 * In for both of the above instances, params and runtimes that can be accessed by the symbol of the function nameIsX may not necessairly
 * be accessible to later callers of the function (e.g. if nameIsX is a member-function which has access to internal parameters of a class
 * that the caller does not. So we cannot just grab the contents from the body of the function by callers. The symbol
 * of the function itself needs to be called in order to get params and runtimes. This is why we remove the arguments but still make the function
 * accessible.
 */
class TransformAnnotatedFunction(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): ElementTransformer<IrFunction>() {

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expr: IrFunction): Boolean =
    expr is IrSimpleFunction && expr.hasAnnotation<CapturedFunction>()

  fun IrSimpleFunction.getSingleReturnExpr(): IrExpression? =
    body?.statements?.singleOrNull().let { it as? IrReturn }?.value

  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(capFunRaw: IrFunction): IrFunction {
    val capFun = capFunRaw as? IrSimpleFunction ?: parseError("The function annotated with @CapturedFunction must be a simple function.", capFunRaw)

    if (!capFun.returnType.isClass<SqlQuery<*>>()) {
      parseError("The function annotated with @CapturedFunction must return a single, siple SqlQuery<T> instance", capFun)
    }
    if (capFun.body == null) {
      parseError("The function body of a @CapturedFunction cannot be empty", capFun)
    }
    val funBody = capFun.body!!
    // TODO allow for SqlExpression and captureValue to be used below
    // run transforms on the body of the function so that a `select`, `capture`, or will be transformed
    //val body = superTransformer.visitBody(funBody, )

    val capFunReturn =
        capFun.getSingleReturnExpr() ?: parseError(Messages.CapturedFunctionFormWrong("Outer form of the captured-function was wrong."), capFun)

    // Add the function value parameters to the parsing context so that the parser treats them as identifiers (instead of dynamics)
    val (rawQueryXR, dynamics) = with (this@LocationContext.withCapturedFunctionParameters(capFun.valueParameters)) {
      on(capFunReturn).match(
        // It can either be a `select { ... }` or a `capture { ... }`
        case(Ir.Call.FunctionUntethered1[Is.of("io.exoquery.capture", "io.exoquery.select"), Is()]).thenThis { funName, _ ->
          // now call the super-transformer on the body of the function
          when (this.funName) {
            "capture" -> TransformCapturedExpression.parseSqlExpression(this, superTransformer)
            "select" -> TransformSelectClause.parseSelectClause(this, superTransformer)
            else -> parseError(Messages.CapturedFunctionFormWrong("Invalid capture-function called in the @CapturedFunction body: '${this.funName}'"), capFunReturn)
          }
        }
      ) ?: parseError(Messages.CapturedFunctionFormWrong("Invalid capture-function body"), capFunReturn)
    }

    val queryXR = rawQueryXR as? XR.Query ?: parseError("The return value of a @CapturedFunction must be a Query ADT instance but it was: ${rawQueryXR.showRaw()}", capFunReturn)

    // The function should not have any parameters since they will be captured in the XR
    val originalParams = capFun.valueParameters
    capFun.valueParameters = emptyList()

    val xrLambdaParams = originalParams.map { Parser.parseValueParamter(it) }
    val xrLambda = XR.FunctionN(params = xrLambdaParams, body = queryXR, loc = capFun.locationXR())
    val params = dynamics.makeParams()
    val newSqlQuery =
      if (dynamics.noRuntimes()) {
        SqlQueryExpr.Uprootable.plantNewUprootable(xrLambda.asQuery(), params)
      } else {
        SqlQueryExpr.Uprootable.plantNewPluckable(xrLambda.asQuery(), dynamics.makeRuntimes(), params)
      }

    capFun.body = ctx.builder.irBlockBody {
      +irReturn(newSqlQuery)
    }
    //error("---------- New CapFun: ----------\n${capFun.dumpKotlinLike()}")

    return capFun
  }

  // Diagnostic error for debugging uproot failure of a particular clause
  fun failOwnerUproot(sym: IrSymbol, output: IrExpression): IrExpression {
    error(
      """|----------- Could not uproot the expression: -----------
         |${sym.owner.dumpKotlinLike()}
         |--- With the following IR: ----
         |${sym.owner.dumpSimple()}""".trimMargin()
    )
    return output
  }
}
