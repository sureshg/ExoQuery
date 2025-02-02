package io.exoquery.xr

import io.exoquery.util.tail

object SelectClauseToXR {
  operator fun invoke(selectClause: SelectClause): XR.Query = run {
    // TODO cover case where there is no from-clause
    // COMPLEX! Need to walk throught the select-clause and recurisvley use `nest` function to nest things in each-other
    val components = selectClause.allComponents()
    if (components.isEmpty()) {
      XR.QueryOf(selectClause.select)
    } else {
      nest(selectClause.from.first().xr, selectClause.from.first().variable, components.tail(), selectClause.select)
    }
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

  fun nest(prev: XR.Query, prevVar: XR.Ident, remaining: List<SX>, output: XR.Expression): XR.Query {
    fun nestRecurse(prev: XR.Query, prevVar: XR.Ident, remaining: List<SX>): XR.Query =
      if (remaining.isEmpty())
        XR.Map(prev, prevVar, output)
      else
      // TODO need to work through this and verify
        when (val curr = remaining.first()) {
          // This is not the 1st FROM clause (which will always be in a head-position
          is SX.From ->
            XR.FlatMap(prev, prevVar, nestRecurse(curr.xr, curr.variable, remaining.tail))
          is SX.Join ->
            XR.FlatMap(
              prev, prevVar,
              nestRecurse(XR.FlatJoin(curr.joinType, curr.onQuery, curr.conditionVariable, curr.condition, curr.loc), curr.variable, remaining.tail)
            )
          is SX.Where ->
            XR.FlatMap(
              prev, prevVar,
              // Since there is no 'new' variable to bind to use use Ident.Unused
              nestRecurse(XR.FlatFilter(curr.condition, curr.loc), XR.Ident.Unused, remaining.tail)
            )
          is SX.GroupBy ->
            XR.FlatMap(
              prev, prevVar,
              nestRecurse(XR.FlatGroupBy(curr.grouping, curr.loc), XR.Ident.Unused, remaining.tail)
            )
          is SX.SortBy ->
            XR.FlatMap(
              prev, prevVar,
              nestRecurse(XR.FlatSortBy(curr.sorting, curr.ordering, curr.loc), XR.Ident.Unused, remaining.tail)
            )
        }
    return nestRecurse(prev, prevVar, remaining)
  }


  //when (curr) {
  //  is SX.From -> XR.FlatMap(prev, curr.variable, curr.xr, curr.loc)
  //  is SX.Join -> XR.FlatMap(prev, curr.variable, XR.FlatJoin(curr.joinType, curr.onQuery, curr.conditionVariable, curr.condition, curr.loc))
  //  is SX.Where -> TODO()
  //  is SX.GroupBy -> TODO()
  //  is SX.SortBy -> TODO()
  //}
}
