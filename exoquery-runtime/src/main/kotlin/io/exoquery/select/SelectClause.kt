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

fun <T> Query<T>.withReifiedIdents(): Query<T> {
  val (reifiedXR, bindIds) = ReifyIdents.ofQuery(binds, xr)
  return QueryContainer<T>(reifiedXR, binds - bindIds)
}


@OptIn(ExoInternal::class) // TODO Not sure if the output here QueryContainer(Ident(SqlVariable)) is right need to look into the shape
class SelectClause<A>(markerName: String) : ProgramBuilder<Query<A>, SqlVariable<A>>({ result -> QueryContainer<A>(XR.Marker(markerName, XR.Ident(result.getVariableName(), XRType.Generic)), DynamicBinds.empty())  }) {

  // TODO search for this call in the IR and see if there's a Val-def on the other side of it and call fromAliased with the name of that
  public suspend fun <R> from(query: Query<R>): SqlVariable<R> =
    // Note find the out the class of R (use an inline?) and make the 1st letter based on it?
    fromAliased(query, query.freshIdent())

  // TODO test select inside select

  // Need some kind of expression parking for this
  //public suspend fun <R> yield(query: Query<R>): SqlVariable<R> =

  public suspend fun <R> fromAliased(query: Query<R>, alias: String): SqlVariable<R> =
    perform { mapping ->
      val sqlVar = SqlVariable<R>(alias)
      val resultQuery = mapping(sqlVar).withReifiedIdents()
      val ident = XR.Ident(sqlVar.getVariableName(), resultQuery.xr.type)
      // No quoted context in this case so only the inner query of this has dynamic binds, we just get those
      QueryContainer<R>(XR.FlatMap(query.xr, ident, resultQuery.xr), query.binds + resultQuery.binds) as Query<A> // TODO Unsafe cast, will this work?
    }

  public suspend fun <Q: Query<R>, R> join(query: Q) = JoinOn<Q, R, A>(query, XR.JoinType.Inner, this, null)

  // TODO public suspend fun <Q: Query<R>, R, A> joinAliased(query: Q, alias: String) = JoinOn(query, XR.JoinType.Inner, this, alias)
}

class JoinOn<Q: Query<R>, R, A>(private val query: Q, private val joinType: XR.JoinType, private val selectClause: SelectClause<A>, private val alias: String?) {
  suspend fun on(cond: context(EnclosedExpression) (R).() -> Boolean): SqlVariable<R> =
    error("The join.on(...) expression of the Query was not inlined")

  // TODO some internal annotation?
  @OptIn(ExoInternal::class)
  suspend fun onExpr(cond: Lambda1Expression, binds: DynamicBinds): SqlVariable<R> =
    with (selectClause) {
      // TODO if (alias != null) beta-reduce 'this' to the alias
      perform { mapping ->
        val sqlVariable = SqlVariable<R>(cond.ident.name)
        val outputQuery = mapping(sqlVariable)
        val ident = XR.Ident(sqlVariable.getVariableName(), outputQuery.xr.type)
        // TODO variable name of the table?
        val freshIdentForCond = run {
          // Need to consider all the alises that could come from any of the other sources before making a new variable for the element.
          val name = freshIdent("x", listOf(cond.xr), listOf(query, outputQuery), listOf(binds))
          XR.Ident(name, cond.ident.type)
        }
        val freshCondBody = BetaReduction(cond.xr.body, cond.ident to freshIdentForCond)
        QueryContainer<R>(XR.FlatMap(
          // Good example of beta reduction
          XR.FlatJoin(joinType, query.xr, freshIdentForCond, freshCondBody), ident, outputQuery.xr), query.binds + binds
          // Maybe there should be some kind of global-flag to disable reduction above
          // XR.FlatJoin(joinType, query.xr, cond.ident, cond.xr.body), ident, outputQuery.xr), query.binds + binds
        ) as Query<A>
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
      case(XR.FlatMap[XR.FlatMap.Is, Is()]).thenThis { head, body -> XR.FlatMap(mark(head), this.ident, mark(body)) },
      // If the tail is a flatMap e.g. FlatMap(?, FlatMap(?, FlatMap(...)))) recurse into the last one in the chain
      case(XR.FlatMap[Is(), XR.FlatMap.Is]).thenThis { head, body -> XR.FlatMap(head, this.ident, mark(body)) },
      // If we are here than we are at the deepest flatMap in the chain since we have reached the marked-value
      case(XR.FlatMap[Is(), XR.Marker[Is(markerId)]]).thenThis { head, (nestedValue) -> XR.Map(head, this.ident, (this.b as XR.Marker).expr!!) }
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

