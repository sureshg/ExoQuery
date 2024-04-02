package io.exoquery.norm

import io.exoquery.*
import io.exoquery.printing.exoPrint
import io.exoquery.xr.StatefulTransformer
import io.exoquery.xr.XR

data class ReifyIdentError(val msg: String): IllegalStateException(msg)

sealed interface Reified {
  val id: BID
  val value: ContainerOfXR

  data class Query(override val id: BID, override val value: io.exoquery.Query<*>): Reified
  data class Expression(override val id: BID, override val value: io.exoquery.SqlExpression<*>): Reified
}

// TODO need to recursively reify binds. Maybe even have an algorithm that allows child
//      bind ids inside of parents elements and recurisvely substitutes until no substitutions can be found
//      For now we just call withReifiedRuntimes on the Query/Expression for the recursion to happen
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
        println("-------------- Searching Binds --------------\n" + exoPrint(binds))
        val runtimeValue = runtimeQueryBinds.get(xr.id) ?: throw ReifyIdentError("Cannot find runtime binding for ${xr}")
        runtimeValue.xr to withState(state + Reified.Query(xr.id, runtimeValue.withReifiedRuntimes()))
      }
      else -> super.invoke(xr)
    }

  override fun invoke(xr: XR.Expression): Pair<XR.Expression, StatefulTransformer<List<Reified>>> =
    when(xr) {
      is XR.RuntimeExpression -> {
        println("-------------- Searching Binds --------------\n" + exoPrint(binds))
        val runtimeValue = runtimeExpressionBinds.get(xr.id) ?: throw ReifyIdentError("Cannot find runtime binding for ${xr}")
        runtimeValue.xr to withState(state + Reified.Expression(xr.id, runtimeValue.withReifiedRuntimes()))
      }
      else -> super.invoke(xr)
    }

  companion object {
    fun ofQueryXR(binds: DynamicBinds, xr: XR.Query) =
      ReifyRuntimes(listOf(), binds)(xr).let { (newXR, state) -> Pair(newXR, state.state) }

    fun ofExpressionXR(binds: DynamicBinds, xr: XR.Expression) =
      ReifyRuntimes(listOf(), binds)(xr).let { (newXR, state) -> Pair(newXR, state.state) }
  }
}
