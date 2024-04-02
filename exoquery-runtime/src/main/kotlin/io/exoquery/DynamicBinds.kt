package io.exoquery

import io.exoquery.xr.XR

// The DynamicBindsAccum syntheisizes this list at Compile-Time. This is the runtime result of the synthesis.
data class DynamicBinds(val list: List<Pair<BID, RuntimeBindValue>>) {

  fun allAncestors(): List<RuntimeBindValue> =
    list.map { it.second }.flatMap {
      when (it) {
        is RuntimeBindValue.RuntimeExpression -> listOf(it.value.binds).flatMap { bind -> bind.allAncestors() }
        is RuntimeBindValue.RuntimeQuery -> listOf(it.value.binds).flatMap { bind -> bind.allAncestors() }
      }
    }

  companion object {
    fun empty() = DynamicBinds(listOf())
  }

  operator fun plus(other: DynamicBinds) = DynamicBinds(this.list + other.list)
  operator fun plus(other: Pair<BID, RuntimeBindValue>) = DynamicBinds(this.list + other)
  operator fun plus(other: List<Pair<BID, RuntimeBindValue>>) = DynamicBinds(this.list + other)
  operator fun minus(other: DynamicBinds) = DynamicBinds(this.list - other.list)
  operator fun minus(bid: BID) = DynamicBinds(this.list.filter { it.first != bid })
  // Note: Might want to use a hash set of `bids` if the list gets big
  operator fun minus(bids: List<BID>) = DynamicBinds(this.list.filter { !bids.contains(it.first) })
}

fun RuntimeBindValue.getXRs(): List<XR> =
  when (this) {
    is RuntimeBindValue.RuntimeExpression -> listOf(this.value.xr)
    is RuntimeBindValue.RuntimeQuery -> listOf(this.value.xr)
  }