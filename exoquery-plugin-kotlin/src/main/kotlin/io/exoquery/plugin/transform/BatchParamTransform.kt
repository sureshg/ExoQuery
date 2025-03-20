package io.exoquery.plugin.transform

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

private class CollectGetValueBatchParams(val batchParam: IrValueParameter) : IrElementVisitorVoid {
  val batchParams = mutableSetOf<IrExpression>()

  override fun visitElement(element: IrElement) {
    if (element is IrGetValue && element.isGetting(batchParam)) {
      batchParams.add(element)
    } else
      element.acceptChildren(this, null)
  }

  //override fun visitGetValue(expression: IrGetValue): Unit {
  //  if (expression.isGetting(batchParam)) {
  //    batchParams.add(expression)
  //  }
  //}
}

private fun IrExpression.containsBatchParam(batchParam: IrValueParameter): Boolean {
  val collect = CollectGetValueBatchParams(batchParam)
  collect.visitExpression(this)
  return collect.batchParams.isNotEmpty()
}

private fun IrGetValue.isGetting(batchParam: IrValueParameter): Boolean =
  this.symbol.owner == batchParam

context(CX.Parsing)
fun IrExpression.containsBatchParam() =
  batchAlias != null && this.containsBatchParam(batchAlias)

context(CX.Parsing)
fun IrGetValue.isBatchParam() =
  batchAlias != null && this.isGetting(batchAlias)
