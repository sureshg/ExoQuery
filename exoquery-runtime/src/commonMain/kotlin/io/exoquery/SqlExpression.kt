package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.xr.XR
import io.exoquery.xr.swapTags

sealed interface ContainerOfXR {
  val xr: XR.U.QueryOrExpression
  // I.e. runtime containers that are used in the expression (if any)
  val runtimes: Runtimes
  val params: Params

  fun rebuild(xr: XR, runtimes: Runtimes, params: Params): ContainerOfXR
}

// Create a wrapper class for runtimes for easy lifting/unlifting
data class Runtimes(val runtimes: List<Pair<BID, ContainerOfXR>>) {
  companion object {
    // When this container is spliced, the ExprModel will look for this actual value in the Ir to tell if there are
    // runtime XR Containers (hence the dynamic-path needs to be followed).
    val Empty = Runtimes(emptyList())
    fun of(vararg runtimes: Pair<BID, ContainerOfXR>) = Runtimes(runtimes.toList())
  }
  operator fun plus(other: Runtimes): Runtimes = Runtimes(runtimes + other.runtimes)
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

data class Param<T>(val id: BID, val value: T)

data class Params(val lifts: List<Param<*>>) {
  operator fun plus(other: Params): Params = Params(lifts + other.lifts)

  companion object {
    fun of(vararg lifts: Param<*>) = Params(lifts.toList())
    // Added this here to be consistent with Runtimes.Empty but unlike Runtimes.Empty it has no
    // special usage (i.e. the parser does not look for this value directly in the IR)
    val Empty = Params(emptyList())
  }
}

// TODO add lifts which will be BID -> ContainerOfEx
// (also need a way to get them easily from the IrContainer)

data class SqlExpression<T>(override val xr: XR.Expression, override val runtimes: Runtimes, override val params: Params): ContainerOfXR {
  fun determinizeDynamics(): SqlExpression<T> = DeterminizeDynamics().ofExpression(this)

  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: Runtimes, params: Params): SqlExpression<T> =
    copy(xr = xr as? XR.Expression ?: xrError("Failed to rebuild SqlExpression with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)
}

internal class DeterminizeDynamics() {
  private var id = 0
  private fun nextId() = "$id".also { id++ }

  private fun recContainer(expr: ContainerOfXR): ContainerOfXR =
    when (expr) {
      is SqlExpression<*> -> recExpr(expr)
      is SqlQuery<*> -> recQuery(expr)
    }

  fun ContainerOfXR.walkParams() =
    params.lifts.map { param ->
      val newBid = BID(nextId())
      val newParam = param.copy(id = newBid)
      Triple(param.id, newBid, newParam)
    }

  fun ContainerOfXR.walkRuntimes() =
    runtimes.runtimes.map { (bid, container) ->
      val newContainer = recContainer(container)
      val newBid = BID(nextId())
      Triple(bid, newBid, newContainer)
    }

  private fun <T> recExpr(expr: SqlExpression<T>): SqlExpression<T> {
    val newParams = expr.walkParams()
    val newRuntimes = expr.walkRuntimes()
    val newXR = expr.xr.swapTags(
      (newParams.map { it.first to it.second } + newRuntimes.map { it.first to it.second }).toMap()
    )
    return SqlExpression(newXR, Runtimes(newRuntimes.map { it.second to it.third }), Params(newParams.map { it.third }))
  }

  private fun <T> recQuery(query: SqlQuery<T>): SqlQuery<T> {
    val newParams = query.walkParams()
    val newRuntimes = query.walkRuntimes()
    val newXR = query.xr.swapTags(
      (newParams.map { it.first to it.second } + newRuntimes.map { it.first to it.second }).toMap()
    )
    return SqlQuery(newXR, Runtimes(newRuntimes.map { it.second to it.third }), Params(newParams.map { it.third }))
  }

  fun <T> ofExpression(expr: SqlExpression<T>): SqlExpression<T> = recExpr(expr)
  fun <T> ofQuery(query: SqlQuery<T>): SqlQuery<T> = recQuery(query)
}





//fun <T> SqlExpression<T>.convertToQuery(): Query<T> = QueryContainer<T>(io.exoquery.xr.XR.ExprToQuery(xr), binds)
//fun <T> Query<T>.convertToSqlExpression(): SqlExpression<T> = SqlExpressionContainer<T>(io.exoquery.xr.XR.QueryToExpr(xr), binds)
