package io.exoquery.norm

import io.exoquery.BID
import io.exoquery.DynamicBinds
import io.exoquery.Query
import io.exoquery.RuntimeBindValue
import io.exoquery.xr.StatefulTransformer
import io.exoquery.xr.XR

data class ReifyIdentError(val msg: String): IllegalStateException(msg)

typealias ReifiedQuery = Pair<BID, Query<*>>
data class ReifyRuntimeQueries internal constructor (override val state: List<ReifiedQuery>, val binds: DynamicBinds): StatefulTransformer<List<ReifiedQuery>> {
  protected fun withState(state: List<Pair<BID, Query<*>>>) = ReifyRuntimeQueries(state, binds)

  val runtimeQueryBinds =
    binds.list.mapNotNull { (bid, runtimeValue) ->
      if (runtimeValue is RuntimeBindValue.RuntimeQuery) bid to runtimeValue.value else null
    }.toMap()

  override fun invoke(xr: XR.Query): Pair<XR.Query, StatefulTransformer<List<ReifiedQuery>>> =
    when(xr) {
      is XR.RuntimeQueryBind -> {
        val runtimeValue = runtimeQueryBinds.get(xr.id) ?: throw ReifyIdentError("Cannot find runtime binding for ${xr}")
        runtimeValue.xr to withState(state + (xr.id to runtimeValue))
      }
      else -> super.invoke(xr)
    }

  companion object {
    fun ofQuery(binds: DynamicBinds, xr: XR.Query) =
      ReifyRuntimeQueries(listOf(), binds)(xr).let { (newXR, state) -> Pair(newXR, state.state) }
  }
}

/**
 * State is the Ids that have been bound. We need them later to
 */
data class ReifyRuntimeIdents internal constructor (override val state: List<BID>, val binds: DynamicBinds): StatefulTransformer<List<BID>> {
  protected fun withState(state: List<BID>) = ReifyRuntimeIdents(state, binds)

  val sqlVariableBinds =
    binds.list.mapNotNull { (bid, runtimeValue) ->
      if (runtimeValue is RuntimeBindValue.SqlVariableIdent) bid to runtimeValue.value else null
    }.toMap()

  override fun invoke(xr: XR.Expression): Pair<XR.Expression, StatefulTransformer<List<BID>>> =
    when(xr) {
      is XR.IdentOrigin -> {
        val runtimeNameValue = sqlVariableBinds.get(xr.runtimeName) ?: throw ReifyIdentError("Cannot find runtime binding for ${xr}")
        XR.Ident(runtimeNameValue, xr.type, xr.loc, xr.visibility) to withState(state + xr.runtimeName)
      }
      else -> super.invoke(xr)
    }

  companion object {
    fun ofQuery(binds: DynamicBinds, xr: XR.Query) =
      ReifyRuntimeIdents(listOf(), binds)(xr).let { (newXR, state) -> Pair(newXR, state.state) }
  }
}

