package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.exoquery.SqlExpression
import io.exoquery.plugin.isClass
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.ParserContext
import io.exoquery.plugin.trees.SqlExpressionExpr
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetClass
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName

class TransformProjectCapture(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrExpression>() {
  context(BuilderContext, CompileLogger)

  override fun matchesBase(expression: IrExpression): Boolean =
    expression.type.isClass<SqlExpression<*>>() && expression is IrGetValue

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrExpression): IrExpression =
    expression.match(
      case(Ir.GetValue[Is()]).then { symbol ->
        symbol.owner.match(
          // E.g. `val x = capture { 123 }` which should have become:
          // `val x = SqlExpression(XR.Int(123), ...)
          // (actuall it will be serialized so: `val x = SqlExpression(unpackExpr("jksnfksjdnf"), ...)`)
          // So it will be Uprootable(XR.Int(123))
          case(Ir.Variable[Is(), SqlExpressionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            // when propagating forward we don't actually need to deserialize the XR contents
            // of the uprootable, just pass it along into the new instance of SqlExpression(unpackExpr(...), ...)
            uprootableExpr.replant(expression)
          }
        ) ?: run {
          error("""
              ----------- Could not uproot the expression: -----------
              ${symbol.owner.dumpKotlinLike()}
              With the following IR:
              ${symbol.owner.dumpSimple()}
            """.trimIndent())
          expression
        }
      }
    ) ?: run {
      error("""
        ----------- Could not re-plant the expression: -----------
        ${expression.dumpKotlinLike()}
        With the following IR:
        ${expression.dumpSimple()}
      """.trimIndent())
      expression
    }
}