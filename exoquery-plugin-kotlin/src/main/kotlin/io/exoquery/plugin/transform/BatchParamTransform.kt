package io.exoquery.plugin.transform

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

class BatchParamTransform {
}


private class CollectGetValueBatchParams(val batchParam: IrValueParameter) : IrElementVisitorVoid {
  val batchParams = mutableSetOf<IrValueParameter>()

  override fun visitGetValue(expression: IrGetValue): Unit {
    if (expression.isGetting(batchParam)) {
      batchParams.add(expression.symbol.owner as IrValueParameter)
    }
    return super.visitGetValue(expression)
  }
}

fun IrElement.containsBatchParam(batchParam: IrValueParameter): Boolean {
  val collect = CollectGetValueBatchParams(batchParam)
  collect.visitElement(this)
  return collect.batchParams.isNotEmpty()
}

private fun IrGetValue.isGetting(batchParam: IrValueParameter): Boolean =
  this.symbol.owner is IrValueParameter
