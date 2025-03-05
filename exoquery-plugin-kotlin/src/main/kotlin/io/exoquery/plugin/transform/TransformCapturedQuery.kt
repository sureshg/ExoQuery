package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.parseError
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.printing.dumpSimple
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName


class TransformCapturedQuery(val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {

  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun matches(expression: IrCall): Boolean =
    ExtractorsDomain.Call.CaptureQuery[Is()].matchesAny(expression)

  // parent symbols are collected in the parent context
  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun transform(expression: IrCall): IrExpression {
    val (xr, dynamics) = parseCapturedQuery(expression, superTransformer)

    val paramsExprModel = dynamics.makeParams()
    val newSqlQuery =
      if (dynamics.noRuntimes()) {
        SqlQueryExpr.Uprootable.plantNewUprootable(xr, paramsExprModel)
      } else {
        SqlQueryExpr.Uprootable.plantNewPluckable(xr, dynamics.makeRuntimes(), paramsExprModel)
      }

    //logger.error("========== Output: ==========\n${newSqlQuery.dumpKotlinLike()}")

    return newSqlQuery
  }

  companion object {
    context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
    fun parseCapturedQuery(expression: IrCall, superTransformer: VisitTransformExpressions)  = run {
      val bodyExpr =
        on(expression).match(
          // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
          case(ExtractorsDomain.Call.CaptureQuery.LambdaBody[Is()]).then { expr ->
            expr
          }
        )
          ?: parseError("The qeury expression has an invalid structure", expression)

      // Transform the contents of `capture { ... }` this is important for several reasons,
      // most notable any kind of variables used inside that need to be inlined e.g:
      // val x = capture { 123 }
      // val y = capture { x.use + 1 } // <- this is what we are transforming
      // Then the `val y` needs to first be transformed into:
      // val y = capture { SqlExpression(XR.Int(123), ...).use + 1 } which will be done by TransformProjectCapture
      // which is called by the superTransformer.visitBlockBody
      val body = superTransformer.recurse(bodyExpr)

      // TODO Needs to convey SourceLocation coordinates, think I did this in terpal-sql somehow
      val p = Parser.scoped { Parser.parseQueryFromBlock(body) }

      //warn("------------ Parsed Query: ------------\n${p.first.showRaw()}\n--------------------------\n${body.dumpSimple()}")
      p
    }
  }
}
