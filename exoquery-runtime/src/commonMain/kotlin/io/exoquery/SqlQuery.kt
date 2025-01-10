package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.xr.XR

data class SqlQuery<T>(override val xr: XR.Query, override val runtimes: Runtimes, override val params: Params): ContainerOfXR {
  fun <R> map(f: (T) -> R): SqlQuery<R> = error("The map expression of the Query was not inlined")
  fun <R> flatMap(f: (T) -> SqlQuery<R>): SqlQuery<R> = error("The flatMap expression of the Query was not inlined")
  fun <R> concatMap(f: (T) -> Iterable<R>): SqlQuery<R> = error("The concatMap expression of the Query was not inlined")
  fun <R> filter(f: (T) -> R): SqlQuery<T> = error("The `filter` expression of the Query was not inlined")
  fun <T> union(other: SqlQuery<T>): SqlQuery<T> = error("The `union` expression of the Query was not inlined")
  fun <T> unionAll(other: SqlQuery<T>): SqlQuery<T> = error("The `unionAll` expression of the Query was not inlined")
  fun distinct(): SqlQuery<T> = error("The `distinct` expression of the Query was not inlined")
  fun <R> distinctBy(): SqlQuery<T> = error("The `distinctBy` expression of the Query was not inlined")
  fun nested(): SqlQuery<T> = error("The `nested` expression of the Query was not inlined")
  fun <R> sortedBy(f: (T) -> R): SqlQuery<T> = error("The sort-by expression of the Query was not inlined")
  fun take(f: Int): SqlQuery<T> = error("The take expression of the Query was not inlined")
  fun drop(f: Int): SqlQuery<T> = error("The drop expression of the Query was not inlined")
  fun size(): SqlQuery<Int> = error("The size expression of the Query was not inlined")


  //fun <R> groupBy(f: (T) -> R): GroupedQuery<T> =  error("The groupBy expression of the Query was not inlined")



  fun determinizeDynamics(): SqlQuery<T> = DeterminizeDynamics().ofQuery(this)

  fun show() = PrintMisc().invoke(this)
}