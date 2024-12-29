package io.exoquery.plugin.trees

import io.exoquery.BID
import io.exoquery.plugin.printing.PrintCompiletimes
import org.jetbrains.kotlin.ir.expressions.IrExpression

class DynamicsAccum {
  // instances of ContainerOfXR (maybe think about something more typed here, or check that the IrExpresssion is a ContainerOfXR when adding)
  private val runtimesCollect = mutableListOf<Pair<BID, IrExpression>>()
  private val paramsCollect = mutableListOf<Pair<BID, IrExpression>>()

  fun makeRuntimes() = RuntimesExpr(runtimesCollect)
  fun makeParams() = ParamsExpr(paramsCollect)

  // TODO have a similar technique for lifts

  fun addRuntime(bindId: BID, bind: IrExpression) {
    runtimesCollect.add(bindId to bind)
  }
  fun addParam(bindId: BID, bind: IrExpression) {
    paramsCollect.add(bindId to bind)
  }

  operator fun plus(other: DynamicsAccum): DynamicsAccum {
    val newBinds = DynamicsAccum()
    newBinds.runtimesCollect.addAll(this.runtimesCollect)
    newBinds.runtimesCollect.addAll(other.runtimesCollect)
    newBinds.paramsCollect.addAll(this.paramsCollect)
    newBinds.paramsCollect.addAll(other.paramsCollect)
    return newBinds
  }

  fun show() = "DynamicsAccum(runtimes=${runtimesCollect.size}, params=${paramsCollect.size})"

  companion object {
    fun newEmpty() = DynamicsAccum()
  }
}
