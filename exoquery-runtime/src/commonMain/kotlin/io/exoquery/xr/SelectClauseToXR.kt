package io.exoquery.xr

import io.exoquery.SX
import io.exoquery.SelectClause
import io.exoquery.util.tail
import io.exoquery.xr.XR

object SelectClauseToXR {
  operator fun invoke(selectClause: SelectClause): XR.Query {
    // TODO cover case where there is no from-clause

    // COMPLEX! Need to walk throught the select-clause and recurisvley use `nest` function to nest things in each-other
    val initial = selectClause.from.first()
    val otherFroms = selectClause.from.drop(1)
    val output = nest(initial.xr, initial.variable, otherFroms)
    // TODO the other clauses
    return output
  }


  /*
  Consider conceptually:
    for {
      x <- from(foo)
      y <- join(bar){stuff}
    } yield (x, y)
  This becomes:
    FlatMap(from(foo), x, FlatMap(join(bar){stuff}, y, Return(Tuple(x, y)))

  However, you can also have multiple froms:
    for {
      x <- from(foo)
      y <- from(bar)
    } yield (x, y)
  This becomes:
    FlatMap(from(foo), x, FlatMap(from(bar), y, Return(Tuple(x, y)))

  Some the `from` clause can also be in tail-position. Now, every single case
  that 'nest' deals with, the from will be in the tail-position.

  Now the most tricky thing that this function does is flip the position of the SX-type clause
  variable (that is on the left) into a FlatMap type variable (that is on the right).
  For example from this:
    val x = foo
    bar(x)
  to this:
    foo.flatMap(x => bar(x))
  which is:
    FlatMap(foo, x, bar(x))
  */
  fun nest(prev: XR.Query, prevVar: XR.Ident, remaining: List<SX>): XR.Query = run {
    if (remaining.isEmpty())
      prev
    else
    // TODO need to work through this and verify
      when (val curr = remaining.first()) {
        // This is not the 1st FROM clause (which will always be in a head-position
        is SX.From ->
          XR.FlatMap(prev, prevVar, nest(curr.xr, curr.variable, remaining.tail))
        is SX.Join ->
          XR.FlatMap(
            prev, prevVar,
            nest(XR.FlatJoin(curr.joinType, curr.onQuery, curr.conditionVariable, curr.condition, curr.loc), curr.variable, remaining.tail)
          )
        is SX.Where ->
          XR.FlatMap(
            prev, prevVar,
            // Since there is no 'new' variable to bind to use use Ident.Unused
            nest(XR.FlatFilter(curr.condition, curr.loc), XR.Ident.Unused, remaining.tail)
          )
        is SX.GroupBy ->
          XR.FlatMap(
            prev, prevVar,
            nest(XR.FlatGroupBy(curr.grouping, curr.loc), XR.Ident.Unused, remaining.tail)
          )
        is SX.SortBy ->
          XR.FlatMap(
            prev, prevVar,
            nest(XR.FlatSortBy(curr.sorting, curr.ordering, curr.loc), XR.Ident.Unused, remaining.tail)
          )
      }
  }


  //when (curr) {
  //  is SX.From -> XR.FlatMap(prev, curr.variable, curr.xr, curr.loc)
  //  is SX.Join -> XR.FlatMap(prev, curr.variable, XR.FlatJoin(curr.joinType, curr.onQuery, curr.conditionVariable, curr.condition, curr.loc))
  //  is SX.Where -> TODO()
  //  is SX.GroupBy -> TODO()
  //  is SX.SortBy -> TODO()
  //}
}