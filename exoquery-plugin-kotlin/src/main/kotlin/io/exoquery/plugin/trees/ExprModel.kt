package io.exoquery.plugin.trees

import io.exoquery.RuntimeBindValue
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.plugin.transform.callMethod
import org.jetbrains.kotlin.ir.expressions.IrExpression

sealed interface RuntimeBindValueExpr {
  // assert that the expression is of type SqlVariable
  data class SqlVariableIdentExpr(val sqlVariableInstance: IrExpression): RuntimeBindValueExpr
}

context (BuilderContext) fun RuntimeBindValueExpr.makeDynamicBindsIr(): IrExpression =
  when (this) {
    is RuntimeBindValueExpr.SqlVariableIdentExpr ->
      // Execute the expression `RuntimeBindValue.SqlVariableIdent()`
      make<RuntimeBindValue.SqlVariableIdent>(sqlVariableInstance.callMethod("getVariableName")())
  }