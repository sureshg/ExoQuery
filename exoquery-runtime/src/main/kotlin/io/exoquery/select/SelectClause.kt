package io.exoquery.select

import io.decomat.*
import io.exoquery.*
import io.exoquery.annotation.ExoInternal
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
class SelectClause<A>(markerName: String) : ProgramBuilder<Query<A>, SqlExpression<A>>(
  { result ->
    QueryContainer<A>(XR.Marker(markerName, result.xr), result.binds)
  }
) {

  // TODO search for this call in the IR and see if there's a Val-def on the other side of it and call fromAliased with the name of that
  public suspend fun <R> from(query: Query<R>): SqlVariable<R> =
    // Note find the out the class of R (use an inline?) and make the 1st letter based on it?
    fromAliased(query, query.freshIdent())

  // TODO test select inside select

  // Need some kind of expression parking for this
  //public suspend fun <R> yield(query: Query<R>): SqlVariable<R> =

  @Suppress("UNCHECKED_CAST")
  public suspend fun <R> fromAliased(query: Query<R>, alias: String): SqlVariable<R> =
    perform { mapping ->
      val sqlVar = SqlVariable<R>(alias)
      // Before going further, take any SqlVariable values and reify the actual name-fields into them
      // since they are specifically returned from the select-value from/join clauses.
      val resultQuery = mapping(sqlVar).withReifiedIdents()
      val ident = XR.Ident(sqlVar.getVariableName(), resultQuery.xr.type)
      // No quoted context in this case so only the inner query of this has dynamic binds, we just get those
      (QueryContainer<A>(XR.FlatMap(query.xr, ident, resultQuery.xr), query.binds + resultQuery.binds)) /*as Query<A>*/
    }

  public suspend fun <Q: Query<R>, R> join(query: Q) =
    JoinOn<Q, R, A>(query, XR.JoinType.Inner, this, null)

  public suspend fun <Q: Query<R>, R> joinAliased(query: Q, alias: String) =
    JoinOn<Q, R, A>(query, XR.JoinType.Inner, this, alias)

  public suspend fun <R> groupBy(f: context(EnclosedExpression) () -> R): Unit =
    error("The groupBy(...) expression of the Query was not inlined")

  public suspend fun <R> groupByExpr(expr: XR.Expression, binds: DynamicBinds): Unit =
    performUnit { mapping ->
      val childQuery = mapping()
      (QueryContainer<A>(XR.FlatMap(XR.FlatGroupBy(expr), XR.Ident("unused", XRType.Unknown), childQuery.xr), childQuery.binds + binds))
    }
}

class JoinOn<Q: Query<R>, R, A>(private val query: Q, private val joinType: XR.JoinType, private val selectClause: SelectClause<A>, private val aliasRaw: String?) {
  suspend fun on(cond: context(EnclosedExpression) (R).() -> Boolean): SqlVariable<R> =
    error("The join.on(...) expression of the Query was not inlined")

  // TODO some internal annotation?
  @OptIn(ExoInternal::class)
  @Suppress("UNCHECKED_CAST")
  suspend fun onExpr(joinIdentRaw: XR.Ident, bodyRaw: XR, onClauseBinds: DynamicBinds): SqlVariable<R> =
    with (selectClause) {
      perform { mapping ->
        val joinIdentTpe = joinIdentRaw.type
        val joinIdentName = aliasRaw ?: joinIdentRaw.name

        val body = bodyRaw as XR.Expression
        val sqlVariable = SqlVariable<R>(joinIdentName)
        val outputQuery = mapping(sqlVariable)
        val ident = XR.Ident(sqlVariable.getVariableName(), outputQuery.xr.type)

        val freshIdentForCond = run {
          // Need to consider all the alises that could come from any of the other sources before making a new variable for the element.
          // however, if the alias is not-null we can rely on just that on being duplicated
          if (aliasRaw != null) {
            val name = freshIdent(joinIdentName, listOf(body), listOf(query, outputQuery), listOf(onClauseBinds))
            XR.Ident(name, joinIdentTpe)
          } else {
            XR.Ident(joinIdentName, joinIdentTpe)
          }
        }
        val freshCondBody = BetaReduction(body, joinIdentRaw to freshIdentForCond)

        // Need to combine the binds of the query that was inside the join-clause together with the joinClause-binds themselves (i.e. the ones
        // produced by the macros that call onExpr) as well as any binds from previous monadic-program calls
        val totalBinds = query.binds + onClauseBinds + outputQuery.binds
        // Finally assemble the output query
        (QueryContainer<R>(XR.FlatMap(
          // Good example of beta reduction
          XR.FlatJoin(joinType, query.xr, freshIdentForCond, freshCondBody), ident, outputQuery.xr), totalBinds
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
      case(XR.FlatMap[XR.FlatMap.Is, Is()]).thenThis { head, id, body -> XR.FlatMap(mark(head), id, mark(body)) },
      // If the tail is a flatMap e.g. FlatMap(?, FlatMap(?, FlatMap(...)))) recurse into the last one in the chain
      case(XR.FlatMap[Is(), XR.FlatMap.Is]).thenThis { head, id, body -> XR.FlatMap(head, id, mark(body)) },
      // If we are here than we are at the deepest flatMap in the chain since we have reached the marked-value
      case(XR.FlatMap[Is(), XR.Marker[Is(markerId)]]).thenThis { head, id, (nestedValue) -> XR.Map(head, id, (this.body as XR.Marker).expr!!) }
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

