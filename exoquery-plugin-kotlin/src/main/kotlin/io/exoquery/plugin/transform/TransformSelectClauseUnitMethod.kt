package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.decomat.on
import io.exoquery.parseError
import io.exoquery.plugin.locationXR
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.trees.ExtractorsDomain
import io.exoquery.plugin.trees.Parser
import io.exoquery.plugin.trees.ParserContext
import io.exoquery.plugin.trees.makeDynamicBindsIr
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

class TransformSelectClauseUnitMethod(override val ctx: BuilderContext, val matcher: ExtractorsDomain.Call.QueryClauseUnitBindMethod, val superTransformer: VisitTransformExpressions): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    matcher.matchesMethod(expression)

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression {
    // For join(addresses).on { id == person.id } :
    //    funExpression would be `id == person.id`. Actually it includes the "hidden" reciver so it would be:
    //    `$this$on.id == person.id`
    val (caller, _, blockBody, annotationData) =
      expression.match(
        case(matcher[Is()]).then { queryCallData -> queryCallData }
      ) ?: parseError("Illegal block on function:\n${Messages.PrintingMessage(expression)}")

    // TODO Recursively transform the block body?

    // parse the `on` clause of the join.on(...)
    val (onLambdaBody, bindsAccum) =
      with(makeParserContext(expression).copy(internalVars)) {
        Parser.parseFunctionBlockBody(blockBody)
      }

    val lifter = makeLifter()
    val onLambdaBodyExpr = lifter.liftXR(onLambdaBody)
    val loc = lifter.liftLocation(expression.locationXR())

    // To transform the TableQuery etc... in the join(<Heree>).on clause before the `on`
    // No scope symbols into caller since it comes Before the on-clause i.e. before any symbols could be created
    val newCaller = caller.transform(superTransformer, internalVars)

    val bindsList = bindsAccum.makeDynamicBindsIr()

    return newCaller.callMethod(annotationData).invoke(onLambdaBodyExpr, bindsList, loc)
  }
}