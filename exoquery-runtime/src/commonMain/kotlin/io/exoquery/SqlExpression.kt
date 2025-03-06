package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.xr.XR
import io.exoquery.xr.swapTags

sealed interface ContainerOfXR


// Less fungible i.e. always top-level and only for actions
sealed interface ContainerOfActionXR: ContainerOfXR {
  val xr: XR.Action
  val runtimes: RuntimeSet
  val params: ParamSet

  fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): ContainerOfActionXR
  fun withNonStrictEquality(): ContainerOfActionXR
}

// Specifically for fungible XR Query and Expression types that are composable (e.g. that can be in a RuntimeSet)
sealed interface ContainerOfFunXR: ContainerOfXR {
  val xr: XR.U.QueryOrExpression
  // I.e. runtime containers that are used in the expression (if any)
  val runtimes: RuntimeSet
  val params: ParamSet

  fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): ContainerOfFunXR
  fun withNonStrictEquality(): ContainerOfFunXR
}

data class SqlExpression<T>(override val xr: XR.Expression, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfFunXR {
  fun determinizeDynamics(): SqlExpression<T> = DeterminizeDynamics().ofExpression(this)

  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlExpression<T> =
    copy(xr = xr as? XR.Expression ?: xrError("Failed to rebuild SqlExpression with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  override fun withNonStrictEquality(): SqlExpression<T> =
    copy(params = params.withNonStrictEquality())
}

internal class DeterminizeDynamics() {
  private var id = 0
  private fun nextId() = "$id".also { id++ }

  private fun recContainer(expr: ContainerOfFunXR): ContainerOfFunXR =
    when (expr) {
      is SqlExpression<*> -> recExpr(expr)
      is SqlQuery<*> -> recQuery(expr)
    }

  fun ContainerOfFunXR.walkParams() =
    params.lifts.map { param ->
      val newBid = BID(nextId())
      val newParam = when (param) {
        is ParamSingle<*> -> param.copy(id = newBid)
        is ParamMulti<*> -> param.copy(id = newBid)
      }
      Triple(param.id, newBid, newParam)
    }

  fun ContainerOfFunXR.walkRuntimes() =
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
    return SqlExpression(newXR, RuntimeSet(newRuntimes.map { it.second to it.third }), ParamSet(newParams.map { it.third }))
  }

  private fun <T> recQuery(query: SqlQuery<T>): SqlQuery<T> {
    val newParams = query.walkParams()
    val newRuntimes = query.walkRuntimes()
    val newXR = query.xr.swapTags(
      (newParams.map { it.first to it.second } + newRuntimes.map { it.first to it.second }).toMap()
    )
    return SqlQuery(newXR, RuntimeSet(newRuntimes.map { it.second to it.third }), ParamSet(newParams.map { it.third }))
  }

  fun <T> ofExpression(expr: SqlExpression<T>): SqlExpression<T> = recExpr(expr)
  fun <T> ofQuery(query: SqlQuery<T>): SqlQuery<T> = recQuery(query)
}





//fun <T> SqlExpression<T>.convertToQuery(): Query<T> = QueryContainer<T>(io.exoquery.xr.XR.ExprToQuery(xr), binds)
//fun <T> Query<T>.convertToSqlExpression(): SqlExpression<T> = SqlExpressionContainer<T>(io.exoquery.xr.XR.QueryToExpr(xr), binds)
