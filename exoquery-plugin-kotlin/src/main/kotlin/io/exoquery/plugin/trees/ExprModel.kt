package io.exoquery.plugin.trees

import io.exoquery.RuntimeBindValue
import io.exoquery.plugin.transform.BuilderContext
import org.jetbrains.kotlin.ir.expressions.IrExpression

// The values of this class and RuntimeBindValue need to match because the Expressions in here generates the IR in RuntimeBindValue
sealed interface RuntimeBind {
  data class ExpressionXR(val expressionElement: IrExpression): RuntimeBind
  data class QueryXR(val queryElement: IrExpression): RuntimeBind
}

context (BuilderContext) fun RuntimeBind.makeDynamicBindsIr(): IrExpression =
  when (this) {
    is RuntimeBind.ExpressionXR ->
      make<RuntimeBindValue.RuntimeExpression>(expressionElement)
    is RuntimeBind.QueryXR ->
      make<RuntimeBindValue.RuntimeQuery>(queryElement)
  }

