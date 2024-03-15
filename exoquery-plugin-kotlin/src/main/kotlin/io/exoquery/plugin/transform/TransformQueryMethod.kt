package io.exoquery.plugin.transform

import io.decomat.*
import io.exoquery.parseError
import io.exoquery.plugin.locationXR
import io.exoquery.xr.XR
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import org.jetbrains.kotlin.ir.expressions.*


class TransformQueryMethod(override val ctx: BuilderContext, val matcher: ExtractorsDomain.QueryFunction, val replacementMethod: String): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    matcher.matchesMethod(expression)

  // parent symbols are collected in the parent context
  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall, superTransformer: VisitTransformExpressions): IrExpression {

    val (caller, funExpression, params, blockBody) =
      on(expression).match(
        // e.g. QueryMap[Is()]
        case(matcher[Is()]).then { queryCallData -> queryCallData }
      ) ?: parseError("Illegal block on function:\n${Messages.PrintingMessage(expression)}")
      // TODO Needs to convey SourceLocation coordinates, look into the 'warn/error' thing to see how to do that
      //      also, should generally just return the expression instead of throwing exceptions in the compiler
      //      perhaps use some kind of FP either-like structure here???

    val lambdaArg = params.first()

    val paramIdentXR = run {
      val name = lambdaArg.name.asString()
      val tpe = TypeParser.parse(lambdaArg.type)
      XR.Ident(name, tpe, expression.locationXR())
    }

    // If there are any maps/filters/flatMaps etc... in the body need to transform them first
    val transformedBlockBody = blockBody.transform(superTransformer, internalVars) as IrBlockBody
    val (bodyXR, bindsAccum) = Parser.parseFunctionBlockBody(transformedBlockBody)

    val lifter = makeLifter()
    val paramIdentExpr = lifter.liftIdent(paramIdentXR)
    val bodyExpr = lifter.liftXR(bodyXR)
    val loc = lifter.liftLocation(expression.locationXR())

    val transformedCaller = caller.transform(superTransformer, internalVars)

    val bindsList = bindsAccum.makeDynamicBindsIr()

    val expressionCall =
      // for:  query.map(p -> p.name)
      // it would be:  (query).callMethodWithType("map", <String>. bindsList())(XR.Function1(Id(p), Prop(p, name))
      transformedCaller.callMethodWithType(replacementMethod, expression.type)(paramIdentExpr, bodyExpr, bindsList, loc)

    return expressionCall
  }


}