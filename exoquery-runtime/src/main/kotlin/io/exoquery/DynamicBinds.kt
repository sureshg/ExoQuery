package io.exoquery

// The DynamicBindsAccum syntheisizes this list at Compile-Time. This is the runtime result of the synthesis.
data class DynamicBinds(val list: List<Pair<BID, RuntimeBindValue>>) {

  fun sqlVars() = list.map { it.second }.filterIsInstance<RuntimeBindValue.SqlVariableIdent>().map { it.value }

  companion object {
    fun empty() = DynamicBinds(listOf())
  }

  fun allVals() = list.map { it.second }.filterIsInstance<RuntimeBindValue.SqlVariableIdent>().map { it.value }

  operator fun plus(other: DynamicBinds) = DynamicBinds(this.list + other.list)
  operator fun plus(other: Pair<BID, RuntimeBindValue>) = DynamicBinds(this.list + other)
  operator fun plus(other: List<Pair<BID, RuntimeBindValue>>) = DynamicBinds(this.list + other)
  operator fun minus(other: DynamicBinds) = DynamicBinds(this.list - other.list)
  operator fun minus(bid: BID) = DynamicBinds(this.list.filter { it.first != bid })
  // Note: Might want to use a hash set of `bids` if the list gets big
  operator fun minus(bids: List<BID>) = DynamicBinds(this.list.filter { !bids.contains(it.first) })
}