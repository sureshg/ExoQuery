package io.exoquery.xr

import io.exoquery.BID
import io.exoquery.DynamicBinds
import io.exoquery.RuntimeBindValue

data class ReifyIdentError(val msg: String): IllegalStateException(msg)

/**
 * State is the Ids that have been bound. We need them later to
 */
data class ReifyIdents internal constructor (override val state: List<BID>, val binds: DynamicBinds): StatefulTransformer<List<BID>> {
  protected fun withState(state: List<BID>) = ReifyIdents(state, binds)

  override val debug: DebugDump = DebugDump()

  val bindMap =
    binds.list.mapNotNull { (bid, runtimeValue) ->
      if (runtimeValue is RuntimeBindValue.SqlVariableIdent) bid to runtimeValue.value else null
    }.toMap()

  override fun invoke(xr: XR.Expression): Pair<XR.Expression, StatefulTransformer<List<BID>>> =
    when(xr) {
      is XR.IdentOrigin -> {
        val runtimeNameValue = bindMap.get(xr.runtimeName) ?: throw ReifyIdentError("Cannot find runtime binding for ${xr}")
        XR.Ident(runtimeNameValue, xr.type, xr.visibility) to withState(state + xr.runtimeName)
      }
      else -> super.invoke(xr)
    }

  companion object {
    fun ofQuery(binds: DynamicBinds, xr: XR.Query) =
      ReifyIdents(listOf(), binds)(xr).let { (newXR, state) -> Pair(newXR, state.state) }
  }
}

