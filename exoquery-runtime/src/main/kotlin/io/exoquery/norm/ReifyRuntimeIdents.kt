package io.exoquery.norm

import io.exoquery.*
import io.exoquery.xr.StatefulTransformer
import io.exoquery.xr.XR

data class ReifyIdentError(val msg: String): IllegalStateException(msg)

sealed interface Reified {
  val id: BID
  val value: ContainerOfXR

  data class Query(override val id: BID, override val value: io.exoquery.Query<*>): Reified
  data class Expression(override val id: BID, override val value: io.exoquery.SqlExpression<*>): Reified
}


data class ReifyRuntimes internal constructor (override val state: List<Reified>, val binds: DynamicBinds): StatefulTransformer<List<Reified>> {
  protected fun withState(state: List<Reified>) = ReifyRuntimes(state, binds)

  val runtimeQueryBinds =
    binds.list.mapNotNull { (bid, runtimeValue) ->
      if (runtimeValue is RuntimeBindValue.RuntimeQuery) bid to runtimeValue.value else null
    }.toMap()

  val runtimeExpressionBinds =
    binds.list.mapNotNull { (bid, runtimeValue) ->
      if (runtimeValue is RuntimeBindValue.RuntimeExpression) bid to runtimeValue.value else null
    }.toMap()

  override fun invoke(xr: XR.Query): Pair<XR.Query, StatefulTransformer<List<Reified>>> =
    when(xr) {
      is XR.RuntimeQuery -> {
        val runtimeValue = runtimeQueryBinds.get(xr.id) ?: throw ReifyIdentError("Cannot find runtime binding for ${xr}")
        runtimeValue.xr to withState(state + Reified.Query(xr.id, runtimeValue))
      }
      else -> super.invoke(xr)
    }

  override fun invoke(xr: XR.Expression): Pair<XR.Expression, StatefulTransformer<List<Reified>>> =
    when(xr) {
      is XR.RuntimeExpression -> {
        val runtimeValue = runtimeExpressionBinds.get(xr.id) ?: throw ReifyIdentError("Cannot find runtime binding for ${xr}")
        runtimeValue.xr to withState(state + Reified.Expression(xr.id, runtimeValue))
      }
      else -> super.invoke(xr)
    }

  companion object {
    fun ofQuery(binds: DynamicBinds, xr: XR.Query) =
      ReifyRuntimes(listOf(), binds)(xr).let { (newXR, state) -> Pair(newXR, state.state) }
  }
}
