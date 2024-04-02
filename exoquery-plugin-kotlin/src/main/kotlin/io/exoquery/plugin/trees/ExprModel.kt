package io.exoquery.plugin.trees

import io.exoquery.RuntimeBindValue
import io.exoquery.plugin.transform.BuilderContext
import org.jetbrains.kotlin.ir.expressions.IrExpression

// The values of this class and RuntimeBindValue need to match because the Expressions in here generates the IR in RuntimeBindValue
sealed interface RuntimeBindValueExpr {
  data class ExpressionXR(val expressionElement: IrExpression): RuntimeBindValueExpr
  data class QueryXR(val queryElement: IrExpression): RuntimeBindValueExpr
}

context (BuilderContext) fun RuntimeBindValueExpr.makeDynamicBindsIr(): IrExpression =
  when (this) {
    is RuntimeBindValueExpr.ExpressionXR ->
      make<RuntimeBindValue.RuntimeExpression>(expressionElement)
    is RuntimeBindValueExpr.QueryXR ->
      make<RuntimeBindValue.RuntimeQuery>(queryElement)
  }

