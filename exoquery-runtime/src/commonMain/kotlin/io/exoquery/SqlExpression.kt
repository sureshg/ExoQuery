package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.xr.XR
import io.exoquery.xr.swapTags

interface ContainerOfXR {
  val xr: XR
  // I.e. runtime containers that are used in the expression (if any)
  val runtimes: Runtimes
  val params: Params
}

// Create a wrapper class for runtimes for easy lifting/unlifting
data class Runtimes(val runtimes: List<Pair<BID, ContainerOfXR>>) {
  companion object {
    // TODO when splicing into the Container use this if the runtimes variable is actually empty
    //      that way we can just check for this when in order to know if a tree can be statically translated or not
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
  }
}

// TODO add lifts which will be BID -> ContainerOfEx
// (also need a way to get them easily from the IrContainer)

data class SqlExpression<T>(override val xr: XR.Expression, override val runtimes: Runtimes, override val params: Params): ContainerOfXR {
  val use: T by lazy { throw IllegalArgumentException("Cannot `use` an SqlExpression outside of a quoted context") }


  fun determinizeDynamics(): SqlExpression<T> = DeterminizeDynamics().ofExpression(this)

  fun show() = PrintMisc().invoke(this)
}

internal class DeterminizeDynamics() {
  private var id = 0
  private fun nextId() = "$id".also { id++ }

  private fun recContainer(expr: ContainerOfXR): ContainerOfXR =
    when (expr) {
      is SqlExpression<*> -> recExpr(expr)
      else -> error("Unsupported")
    }

  private fun <T> recExpr(expr: SqlExpression<T>): SqlExpression<T> {
    val newParams = expr.params.lifts.map { param ->
      val newBid = BID(nextId())
      val newParam = param.copy(id = newBid)
      Triple(param.id, newBid, newParam)
    }

    val newRuntimes = expr.runtimes.runtimes.map { (bid, container) ->
      val newContainer = recContainer(container)
      val newBid = BID(nextId())
      Triple(bid, newBid, newContainer)
    }

    val newXR = expr.xr.swapTags(
      (newParams.map { it.first to it.second } + newRuntimes.map { it.first to it.second }).toMap()
    )
    return SqlExpression(newXR, Runtimes(newRuntimes.map { it.second to it.third }), Params(newParams.map { it.third }))
  }

  fun <T> ofExpression(expr: SqlExpression<T>): SqlExpression<T> = recExpr(expr)
}





//fun <T> SqlExpression<T>.convertToQuery(): Query<T> = QueryContainer<T>(io.exoquery.xr.XR.QueryOf(xr), binds)
//fun <T> Query<T>.convertToSqlExpression(): SqlExpression<T> = SqlExpressionContainer<T>(io.exoquery.xr.XR.ValueOf(xr), binds)