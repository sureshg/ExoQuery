package io.exoquery.plugin.trees

import io.exoquery.BID
import io.exoquery.plugin.printing.PrintCompiletimes
import org.jetbrains.kotlin.ir.expressions.IrExpression

class DynamicsAccum {
  // instances of ContainerOfXR (maybe think about something more typed here, or check that the IrExpresssion is a ContainerOfXR when adding)
  private val runtimesCollect = mutableListOf<Pair<BID, IrExpression>>()

  fun makeRuntimes() = RuntimesExpr(runtimesCollect)

  // TODO have a similar technique for lifts

  fun add(bindId: BID, bind: IrExpression) {
    runtimesCollect.add(bindId to bind)
  }

  operator fun plus(other: DynamicsAccum): DynamicsAccum {
    val newBinds = DynamicsAccum()
    newBinds.runtimesCollect.addAll(this.runtimesCollect)
    newBinds.runtimesCollect.addAll(other.runtimesCollect)
    return newBinds
  }

  fun show() = PrintCompiletimes()(runtimesCollect)

  companion object {
    fun newEmpty() = DynamicsAccum()
  }
}
