package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.parseError
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.trees.ExtractorsDomain
import io.exoquery.plugin.trees.Parser
import io.exoquery.plugin.trees.SqlActionExpr
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

class TransformCapturedAction(val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {
  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun matches(expression: IrCall): Boolean =
    ExtractorsDomain.Call.CaptureAction[Is.Companion()].matchesAny(expression)

  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun transform(expression: IrCall): IrExpression {
    val (xr, dynamics) = parseCapturedAction(expression, superTransformer)

    val paramsExprModel = dynamics.makeParams()
    val newSqlAction =
      if (dynamics.noRuntimes()) {
        SqlActionExpr.Uprootable.plantNewUprootable(xr, paramsExprModel)
      } else {
        SqlActionExpr.Uprootable.plantNewPluckable(xr, dynamics.makeRuntimes(), paramsExprModel)
      }

    //logger.error("========== Output: ==========\n${newSqlAction.dumpKotlinLike()}")

    return newSqlAction
  }
  companion object {
    context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
    fun parseCapturedAction(expression: IrCall, superTransformer: VisitTransformExpressions)  = run {
      val bodyExpr =
        on(expression).match(
          // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
          case(ExtractorsDomain.Call.CaptureAction.LambdaOutput[Is.Companion()]).then { expr ->
            expr
          }
        )
          ?: parseError(Messages.InvalidSqlActionFunctionBody(), expression)

      val body = superTransformer.recurse(bodyExpr)
      Parser.scoped { Parser.parseAction(body) }
    }
  }

}
