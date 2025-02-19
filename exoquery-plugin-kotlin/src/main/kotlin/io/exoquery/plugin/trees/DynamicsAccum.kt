package io.exoquery.plugin.trees

import io.exoquery.BID
import io.exoquery.plugin.printing.PrintCompiletimes
import org.jetbrains.kotlin.ir.expressions.IrExpression

class DynamicsAccum {
  // instances of ContainerOfXR (maybe think about something more typed here, or check that the IrExpresssion is a ContainerOfXR when adding)
  private val runtimesCollect = mutableListOf<Pair<BID, IrExpression>>()
  // Other instances of SqlExpression or SqlQuery (i.e. from other uprootable SqlExpression instances that we need to compose)
  private val allRuntimesCollect = mutableListOf<IrExpression>()

  // instances of Params (maybe think about something more typed here, or check that the IrExpresssion is a Params when adding)
  private val paramsCollect = mutableListOf<ParamBind>()
  // Other instances of SqlExpression or SqlQuery (i.e. from other uprootable SqlExpression instances that we need to compose)
  private val allParamsCollect = mutableListOf<IrExpression>()

  fun makeRuntimes() = RuntimesExpr(runtimesCollect, allParamsCollect)
  fun makeParams() = ParamsExpr(paramsCollect, allParamsCollect)

  // TODO have a similar technique for lifts

  fun noRuntimes():Boolean = runtimesCollect.isEmpty()

  fun addRuntime(bindId: BID, bind: IrExpression) {
    runtimesCollect.add(bindId to bind)
  }
  fun addAllRuntimes(sqlExpressionInstance: IrExpression) {
    allRuntimesCollect.add(sqlExpressionInstance)
  }

  fun addAllParams(sqlExpressionInstance: IrExpression) {
    allParamsCollect.add(sqlExpressionInstance)
  }
  fun addParam(bindId: BID, value: IrExpression, paramType: ParamBind.Type) {
    // Note that the bind-expression is the same as the value of the parameter is all cases except for a ValueWithSerializer
    // where the .value field is taken out of it first
    paramsCollect.add(ParamBind(bindId, value, paramType))
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
