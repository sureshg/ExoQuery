package io.exoquery

import io.exoquery.serial.ParamSerializer


/*
 * val expr: @Captured SqlExpression<Int> = capture { foo + bar }
 * val query: SqlQuery<Person> = capture { Table<Person>() }
 * val query2: SqlQuery<Int> = capture { query.map { p -> p.age } }
 *
 * so:
 * // Capturing a generic expression returns a SqlExpression
 * fun <T> capture(block: () -> T): SqlExpression<T>
 * for example:
 * {{{
 * val combo: SqlExpression<Int> = capture { foo + bar }
 * }}}
 *
 * // Capturing a SqlQuery returns a SqlQuery
 * fun <T> capture(block: () -> SqlQuery<T>): SqlQuery<T>
 * for example:
 * {{{
 * val query: SqlQuery<Person> = capture { Table<Person>() }
 * }}}
 */
sealed interface Param<T: Any> {
  val id: BID
  val serial: ParamSerializer<*>
  fun withNonStrictEquality(): Param<T>
  fun showValue(): String
  fun withNewBid(newBid: BID): Param<T>
}




// TODO need to have multiple-version of Param
// TODO also in the dsl get rid of params that takes a list of ValueWithSerializer instances.
//      any params used in a collection need to have the same serializer
data class ParamMulti<T: Any>(override val id: BID, val value: List<T>, override val serial: ParamSerializer<T>): Param<T> {
  override fun withNonStrictEquality(): ParamMulti<T> =
    copy(serial = serial.withNonStrictEquality())
  override fun showValue() = value.toString()
  override fun withNewBid(newBid: BID): ParamMulti<T> = copy(id = newBid)
}

data class ParamSingle<T: Any>(override val id: BID, val value: T, override val serial: ParamSerializer<T>): Param<T> {
  override fun withNonStrictEquality(): ParamSingle<T> =
    copy(serial = serial.withNonStrictEquality())
  override fun showValue() = value.toString()
  override fun withNewBid(newBid: BID): ParamSingle<T> = copy(id = newBid)
}

data class ParamSet(val lifts: List<Param<*>>) {
  operator fun plus(other: ParamSet): ParamSet = ParamSet(lifts + other.lifts)
  fun withNonStrictEquality(): ParamSet = ParamSet(lifts.map { it.withNonStrictEquality() })

  companion object {
    fun of(vararg lifts: Param<*>) = ParamSet(lifts.toList())
    // Added this here to be consistent with Runtimes.Empty but unlike Runtimes.Empty it has no
    // special usage (i.e. the parser does not look for this value directly in the IR)
    val Empty = ParamSet(emptyList())
  }
}
