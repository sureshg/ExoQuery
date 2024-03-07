package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.parseError
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.trees.ExtractorsDomain
import io.exoquery.plugin.trees.Parser
import io.exoquery.plugin.trees.ParserContext
import io.exoquery.plugin.trees.makeDynamicBindsIr
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

class TransformSelect(override val ctx: BuilderContext): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    ExtractorsDomain.Call.`select(fun)`.matchesMethod(expression)

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall, superTransformer: VisitTransformExpressions): IrExpression {
    val (funExpression, params, blockBody) =
      on(expression).match(
        case(ExtractorsDomain.Call.`select(fun)`[Is()]).then { callData -> callData }
      ) ?: parseError("Illegal block on function:\n${Messages.PrintingMessage(expression)}")

    // Could there be Query<*> inside of the block bodies for groupBy/select etc...?
    val transformedBlockBody = blockBody.transform(superTransformer, internalVars) as IrBlockBody

    // parse the `on` clause of the join.on(...)
    val (onLambdaBody, bindsAccum) =
      with(makeParserContext(expression).copy(internalVars)) {
        Parser.parseFunctionBlockBody(transformedBlockBody)
      }

    val lifter = makeLifter()
    val onLambdaBodyExpr = lifter.liftXR(onLambdaBody)

    // To transform the TableQuery etc... in the join(<Heree>).on clause before the `on`
    // No scope symbols into caller since it comes Before the on-clause i.e. before any symbols could be created

    val bindsList = bindsAccum.makeDynamicBindsIr()

    return callMethodWithType("io.exoquery", "selectExpr", expression.type).invoke(onLambdaBodyExpr, bindsList)
  }
}