package io.exoquery

import io.exoquery.xr.XR

interface ContainerOfXR {
  val xr: XR
  // I.e. runtime containers that are used in the expression (if any)
  val runtimes: Runtimes
}

// Create a wrapper class for runtimes for easy lifting/unlifting
data class Runtimes(val runtimes: List<Pair<BID, ContainerOfXR>>) {
  companion object {
    // TODO when splicing into the Container use this if the runtimes variable is actually empty
    //      that way we can just check for this when in order to know if a tree can be statically translated or not
    val Empty = Runtimes(emptyList())
  }
}
// TODO similar class for lifts

/*
 * val expr: @Captured SqlExpression<Int> = capture { foo + bar }
 * val query: SqlQuery<Person> = capture { Table<Person>() }
 * val query2: SqlQuery<Int> = capture { query.map { p -> p.age } }
 *
 * so:
 * // Capturing a generic expression returns a SqlExpression
 * fun <T> capture(block: () -> T): SqlExpression<T>
 * // Capturing a SqlQuery returns a SqlQuery
 * fun <T> capture(block: () -> SqlQuery<T>): SqlQuery<T>
 */

// TODO add lifts which will be BID -> ContainerOfEx
// (also need a way to get them easily from the IrContainer)

//data class SqlExpression<T>(override val xr: XR.Expression, override val runtimes: Runtimes): ContainerOfXR


data class SqlExpression<T>(val stuff: String)


//fun <T> SqlExpression<T>.convertToQuery(): Query<T> = QueryContainer<T>(io.exoquery.xr.XR.QueryOf(xr), binds)
//fun <T> Query<T>.convertToSqlExpression(): SqlExpression<T> = SqlExpressionContainer<T>(io.exoquery.xr.XR.ValueOf(xr), binds)