package io.exoquery.plugin.transform

import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

context (ScopeContext) fun VisitTransformExpressions.visitExpression(expression: IrExpression) =
  visitExpression(expression, transformerScope)

context (ScopeContext) fun VisitTransformExpressions.visitBlockBody(body: IrBlockBody) =
  visitBlockBody(body, transformerScope)

context (ScopeContext) fun VisitTransformExpressions.visitCall(expression: IrCall) =
  visitCall(expression, transformerScope)