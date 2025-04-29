package io.exoquery.plugin.trees

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

object IrTraversals {
  class CollectGetValue : IrElementVisitorVoid {
    val values = mutableListOf<IrGetValue>()

    // Need to override the visitor at the visitElement function because the visitor bubbles up instead of down
    // i.e. if a generality of IrGetElement is used (e.g. the actual instance of the thing that is a IrGetValue)
    // is stored in an IrExpression variable etc... then the visitGetValue case will not be called.
    override fun visitElement(element: IrElement) {
      if (element is IrGetValue)
        values.add(element)
      else
        element.acceptChildren(this, null)
    }
  }

  fun collectGetValue(expression: IrExpression): List<IrGetValue> {
    val collector = CollectGetValue()
    collector.visitElement(expression)
    return collector.values
  }
}
