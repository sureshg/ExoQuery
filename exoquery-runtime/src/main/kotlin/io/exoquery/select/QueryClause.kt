package io.exoquery.select

import io.decomat.*
import io.exoquery.*
import io.exoquery.annotation.ExoInternal
import io.exoquery.annotation.QueryClauseAliasedMethod
import io.exoquery.annotation.QueryClauseUnitBind
import io.exoquery.xr.*


fun XR.collectBinds() =
  CollectXR.byType<XR.Ident>(this).map { it.name }

fun <T> Query<T>.freshIdent(prefix: String = "x") =
  freshIdent(prefix, listOf(), listOf(this))

fun freshIdent(prefix: String = "x", xrs: List<XR> = listOf(), queries: List<Query<*>> = listOf(), binds: List<DynamicBinds> = listOf()): String =
  freshIdentFrom(prefix,
    (
      xrs.flatMap { it.collectBinds() } +
        queries.flatMap { it.xr.collectBinds() + it.binds.allVals() } +
        binds.flatMap { it.sqlVars() }
      ).toSet())

private fun freshIdentFrom(prefix: String = "x", allBindVars: Set<String>): String {
  // Also considering binds despite the fact that OrigIdent should not even be there in the Ast anymore
  var index = 0
  var highest = prefix
  while (allBindVars.contains(highest)) {
    index++
    highest = "${prefix}${index}"
  }
  // once we found a variable that's not contained in the tree, return it
  // (since this is the highest-numbered identifier with the prefix)
  return highest
}


@OptIn(ExoInternal::class) // TODO Not sure if the output here QueryContainer(Ident(SqlVariable)) is right need to look into the shape
class QueryClause<A>(markerName: String) : ProgramBuilder<Query<A>, SqlExpression<A>>(
  { result ->
    QueryContainer<A>(XR.Marker(markerName, result.xr, XR.Location.Synth), result.binds)
  }
) {

  @QueryClauseAliasedMethod("fromAliased")
  public suspend fun <R> from(query: Query<R>): SqlVariable<R> =
    fromAliased(query, query.freshIdent(), XR.Location.Synth)

  // TODO Delgate to this as well
  //public suspend fun <R> fromUnaliased(query: Query<R>, loc: XR.Location): SqlVariable<R> =
  //  // Note find the out the class of R (use an inline?) and make the 1st letter based on it?
  //  fromAliased(query, query.freshIdent(), loc)

  // TODO test select inside select

  // Need some kind of expression parking for this
  //public suspend fun <R> yield(query: Query<R>): SqlVariable<R> =

  @Suppress("UNCHECKED_CAST")
  @OptIn(ExoInternal::class)
  public suspend fun <R> fromAliased(query: Query<R>, alias: String, loc: XR.Location): SqlVariable<R> =
    perform { mapping ->
      val sqlVar = SqlVariable<R>(alias)
      // Before going further, take any SqlVariable values and reify the actual name-fields into them
      // since they are specifically returned from the select-value from/join clauses.
      val resultQuery = mapping(sqlVar).withReifiedIdents()
      val ident = XR.Ident(sqlVar.getVariableName(), resultQuery.xr.type, loc)
      // No quoted context in this case so only the inner query of this has dynamic binds, we just get those
      (QueryContainer<A>(XR.FlatMap(query.xr, ident, resultQuery.xr, loc), query.binds + resultQuery.binds)) /*as Query<A>*/
    }

  @Suppress("UNUSED_PARAMETER")
  @QueryClauseAliasedMethod("joinAliased")
  public suspend fun <Q: Query<R>, R> join(query: Q) =
    JoinOn<Q, R, A>(query, XR.JoinType.Inner, this, null)
  @OptIn(ExoInternal::class)
  public suspend fun <Q: Query<R>, R> joinAliased(query: Q, alias: String, loc: XR.Location) =
    JoinOn<Q, R, A>(query, XR.JoinType.Inner, this, alias)

  @QueryClauseUnitBind("groupByExpr")
  public suspend fun <R> groupBy(f: context(EnclosedExpression) () -> R): Unit =
    error("The groupBy(...) expression of the Query was not inlined")
  @OptIn(ExoInternal::class)
  public suspend fun <R> groupByExpr(expr: XR.Expression, binds: DynamicBinds, loc: XR.Location): Unit =
    performUnit { mapping ->
      val childQuery = mapping()
      (QueryContainer<A>(XR.FlatMap(XR.FlatGroupBy(expr, loc), XR.Ident.Unused, childQuery.xr, loc), childQuery.binds + binds))
    }

  @QueryClauseUnitBind("sortedByExpr")
  public suspend fun <R> sortedBy(f: context(EnclosedExpression) () -> R): Unit =
    error("The sortedBy(...) expression of the Query was not inlined")
  @OptIn(ExoInternal::class)
  public suspend fun <R> sortedByExpr(expr: XR.Expression, binds: DynamicBinds, loc: XR.Location): Unit =
    performUnit { mapping ->
      val childQuery = mapping()
      (QueryContainer<A>(XR.FlatMap(XR.FlatSortBy(expr, ordering = XR.Ordering.Asc, loc), XR.Ident.Unused, childQuery.xr, loc), childQuery.binds + binds))
    }

  @QueryClauseUnitBind("sortedByDescendingExpr")
  public suspend fun <R> sortedByDescending(f: context(EnclosedExpression) () -> R): Unit =
    error("The sortedBy(...) expression of the Query was not inlined")
  @OptIn(ExoInternal::class)
  public suspend fun <R> sortedByDescendingExpr(expr: XR.Expression, binds: DynamicBinds, loc: XR.Location): Unit =
    performUnit { mapping ->
      val childQuery = mapping()
      (QueryContainer<A>(XR.FlatMap(XR.FlatSortBy(expr, ordering = XR.Ordering.Desc, loc), XR.Ident.Unused, childQuery.xr, loc), childQuery.binds + binds))
    }

  @QueryClauseUnitBind("sortedByUsingExpr")
  public suspend fun <R> sortedByUsing(f: context(EnclosedExpression) () -> R): SortedByOrders<A> =
    error("The sortedBy(...) expression of the Query was not inlined")
  @OptIn(ExoInternal::class)
  public suspend fun <R> sortedByUsingExpr(expr: XR.Expression, binds: DynamicBinds, loc: XR.Location): SortedByOrders<A> =
    SortedByOrders(expr, binds, loc, this)

  // TODO sortedByDescending
  // TODO sorted { expr }(Asc, Desc, etc.... <- make a DSL for this)

  @QueryClauseUnitBind("whereExpr")
  public suspend fun <R> where(f: context(EnclosedExpression) () -> R): Unit =
    error("The where(...) expression of the Query was not inlined")
  @OptIn(ExoInternal::class)
  public suspend fun <R> whereExpr(expr: XR.Expression, binds: DynamicBinds, loc: XR.Location): Unit =
    performUnit { mapping ->
      val childQuery = mapping()
      (QueryContainer<A>(XR.FlatMap(XR.FlatFilter(expr, loc), XR.Ident.Unused, childQuery.xr, loc), childQuery.binds + binds))
    }
}

//expr: XR.Expression, binds: DynamicBinds, loc: XR.Location
class SortedByOrders<A>(private val expr: XR.Expression, private val binds: DynamicBinds, private val loc: XR.Location, private val queryClause: QueryClause<A>) {
  suspend operator fun invoke(vararg orders: SortOrder) =
    with (queryClause) {
      performUnit { mapping ->
        val childQuery = mapping()
        (QueryContainer<A>(XR.FlatMap(XR.FlatSortBy(expr, ordering = XR.Ordering.fromDslOrdering(orders.toList()), loc), XR.Ident.Unused, childQuery.xr, loc), childQuery.binds + binds))
      }
    }
}

class JoinOn<Q: Query<R>, R, A>(private val query: Q, private val joinType: XR.JoinType, private val queryClause: QueryClause<A>, private val aliasRaw: String?) {
  suspend fun on(cond: context(EnclosedExpression) (R).() -> Boolean): SqlVariable<R> =
    error("The join.on(...) expression of the Query was not inlined")

  // TODO some internal annotation?
  @OptIn(ExoInternal::class)
  @Suppress("UNCHECKED_CAST")
  suspend fun onExpr(joinIdentRaw: XR.Ident, bodyRaw: XR, onClauseBinds: DynamicBinds, loc: XR.Location): SqlVariable<R> =
    with (queryClause) {
      perform { mapping ->
        val joinIdentTpe = joinIdentRaw.type
        val joinIdentName = aliasRaw ?: joinIdentRaw.name

        val body = bodyRaw as XR.Expression
        val sqlVariable = SqlVariable<R>(joinIdentName)
        val outputQuery = mapping(sqlVariable)
        val ident = XR.Ident(sqlVariable.getVariableName(), outputQuery.xr.type, loc)

        val freshIdentForCond = run {
          // Need to consider all the alises that could come from any of the other sources before making a new variable for the element.
          // however, if the alias is not-null we can rely on just that on being duplicated
          if (aliasRaw != null) {
            val name = freshIdent(joinIdentName, listOf(body), listOf(query, outputQuery), listOf(onClauseBinds))
            XR.Ident(name, joinIdentTpe, loc)
          } else {
            XR.Ident(joinIdentName, joinIdentTpe, loc)
          }
        }
        val freshCondBody = BetaReduction(body, joinIdentRaw to freshIdentForCond)

        // Need to combine the binds of the query that was inside the join-clause together with the joinClause-binds themselves (i.e. the ones
        // produced by the macros that call onExpr) as well as any binds from previous monadic-program calls
        val totalBinds = query.binds + onClauseBinds + outputQuery.binds
        // Finally assemble the output query
        (QueryContainer<R>(XR.FlatMap(
          // Good example of beta reduction
          XR.FlatJoin(joinType, query.xr, freshIdentForCond, freshCondBody, loc), ident, outputQuery.xr, loc), totalBinds
          // Maybe there should be some kind of global-flag to disable reduction above
          // XR.FlatJoin(joinType, query.xr, cond.ident, cond.xr.body), ident, outputQuery.xr), query.binds + binds
        ) as Query<A>)
      }
    }
}

class InnerMost(private val markerId: String) {
  fun findAndMark(xr: XR.Query) = mark(xr)

  private fun mark(xr: XR.Query): XR.Query =
    when(xr) {
      is XR.FlatMap -> markFlatMap(xr)
      else -> xr
    }

  private fun markFlatMap(q: XR.FlatMap): XR.Query =
    on(q).match(
      // if head is a map it's something like FlatMap(FlatMap(...FlatMap), ...) so get to innermost one on head & mark
      // then recurse back from the outer structure
      case(XR.FlatMap[XR.FlatMap.Is, Is()]).thenThis { head, id, body -> XR.FlatMap.cs(mark(head), id, mark(body)) },
      // If the tail is a flatMap e.g. FlatMap(?, FlatMap(?, FlatMap(...)))) recurse into the last one in the chain
      case(XR.FlatMap[Is(), XR.FlatMap.Is]).thenThis { head, id, body -> XR.FlatMap.cs(head, id, mark(body)) },
      // If we are here than we are at the deepest flatMap in the chain since we have reached the marked-value
      case(XR.FlatMap[Is(), XR.Marker[Is(markerId), Is()]]).thenThis { head, id, (nestedValue, _) -> XR.Map(head, id, (this.body as XR.Marker).expr!!, loc) }
    ) ?: q

}

/**
 * Query<Person> -> Query<Tuple>
 * person.map(p => tupleOf(p.name))
 */


//fun <T: Element, R: Element> Query<T>.flatMap(f: (T) -> Query<R>) = FlatMap(this, f(this.ent.onBind()))
//fun <T: Element, R: Element> Query<T>.map(f: (T) -> R) = Map(this, f(this.ent.onBind()))
////fun <T: Entity, E> Query<T>.map(f: (T) -> Expression<E>) = Map(this, Entity.Single(f(this.ent.onBind())))
//
//fun <A: Element> Query<A>.nested(): Query<A> = Nested(this)
//
////
//

//
//class IdGrantor()

