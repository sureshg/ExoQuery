package io.exoquery.plugin.transform

import io.exoquery.plugin.transform.CX
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

context(CX.Symbology, CX.QueryAccum) fun makeVisitorContext() = VisitorContext(symbolSet, accum)

context (CX.Symbology, CX.QueryAccum) fun VisitTransformExpressions.visitExpression(expression: IrExpression) =
  visitExpression(expression, makeVisitorContext())

context (CX.Symbology, CX.QueryAccum) fun VisitTransformExpressions.visitBlockBody(body: IrBlockBody) =
  visitBlockBody(body, makeVisitorContext())

context (CX.Symbology, CX.QueryAccum) fun VisitTransformExpressions.visitCall(expression: IrCall) =
  visitCall(expression, makeVisitorContext())
