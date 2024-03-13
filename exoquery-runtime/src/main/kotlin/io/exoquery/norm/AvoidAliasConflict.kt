package io.exoquery.norm

import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.BetaReduction
import io.exoquery.xr.StatefulTransformer
import io.exoquery.xr.XR
import io.exoquery.xr.XR.*

data class AvoidAliasConflict(override val state: Set<String>, val detemp: Boolean, val traceConfig: TraceConfig): StatefulTransformer<Set<String>> {

  fun Recurse(state: Set<String>) =
    AvoidAliasConflict(state, detemp, traceConfig)

  val trace: Tracer =
    Tracer(TraceType.AvoidAliasConflict, traceConfig, 1)

  /**
   * I.e. we consider to be something to be "unaliased" if it is an Entity or Infix most often it will be something like
   * `Map(head:Entity(people), id, body)` then the `Map` won't be considered aliased because it's `head` is an XR.Entity.
   * In case there are some single-element things like `Map(Distinct(Entity(people)), ...)`, or `Map(Take(Entity(people), ...), ...)`
   * etc... recursively go inside of those things to see if an XR.Entity is inside.
   */
  fun XR.isUnaliased(): Boolean =
    when (this) {
      is Nested -> head.isUnaliased()
      is Take -> head.isUnaliased()
      is Drop -> head.isUnaliased()
      // Aggregation is not a query in ExoQuery
      //is Aggregation -> head.isUnaliased()
      is Distinct -> head.isUnaliased()
      is Entity, is Infix -> true
      else -> false
    }

// Scala
//  private def isUnaliased(q: Ast): Boolean =
//      q match {
//        case Nested(q: Query)         => isUnaliased(q)
//        case Take(q: Query, _)        => isUnaliased(q)
//        case Drop(q: Query, _)        => isUnaliased(q)
//        case Aggregation(_, q: Query) => isUnaliased(q)
//        case Distinct(q: Query)       => isUnaliased(q)
//        case _: Entity | _: Infix     => true
//        case _                        => false
//      }


  private fun freshIdent(x: Ident, state: Set<String> = this.state): Ident =
    if (x.isTemporary() && detemp)
      dedupeIdent(Ident("x", x.type, x.loc), state)
    else
      dedupeIdent(x, state)

//  override def apply(qq: Query): (Query, StatefulTransformer[Set[IdentName]]) =
//    trace"Uncapture $qq ".andReturnIf {
//      qq match {
//
//        case FlatMap(Unaliased(q), x, p) =>
//          apply(x, p)(FlatMap(q, _, _))
//
//        case ConcatMap(Unaliased(q), x, p) =>
//          apply(x, p)(ConcatMap(q, _, _))
//
//        case Map(Unaliased(q), x, p) =>
//          apply(x, p)(Map(q, _, _))
//
//        case Filter(Unaliased(q), x, p) =>
//          apply(x, p)(Filter(q, _, _))
//
//        case GroupByMap(Unaliased(q), byId, byBody, mapId, mapBody) =>
//          val ((byId1, byBody1), s1)  = apply(byId, byBody)((_, _))
//          val ((toId1, mapBody1), s2) = apply(mapId, mapBody)((_, _))
//          (
//            GroupByMap(q, byId1, byBody1, toId1, mapBody1),
//            new AvoidAliasConflict(s1.state ++ s2.state, detemp, traceConfig)
//          )
//
//        case DistinctOn(Unaliased(q), x, p) =>
//          apply(x, p)(DistinctOn(q, _, _))
//
//        case m @ FlatMap(_, _, _) =>
//          recurseAndApply(m)(m => (m.query, m.alias, m.body))(FlatMap(_, _, _))
//
//        case m @ ConcatMap(_, _, _) =>
//          recurseAndApply(m)(m => (m.query, m.alias, m.body))(ConcatMap(_, _, _))
//
//        case m @ Map(_, _, _) =>
//          recurseAndApply(m)(m => (m.query, m.alias, m.body))(Map(_, _, _))
//
//        case m @ Filter(_, _, _) =>
//          recurseAndApply(m)(m => (m.query, m.alias, m.body))(Filter(_, _, _))
//
//        case m @ GroupByMap(_, _, _, _, _) =>
//          val (newQuery, newTrans) = super.apply(m)
//          val m1                   = newQuery.asInstanceOf[GroupByMap]
//          val (List((byAlias, byBody), (mapAlias, mapBody)), state) =
//            applyBodies(List(m1.byAlias -> m1.byBody, m1.mapAlias -> m1.mapBody))
//          (
//            GroupByMap(m1.query, byAlias, byBody, mapAlias, mapBody),
//            new AvoidAliasConflict(newTrans.state ++ state, detemp, traceConfig)
//          )


  override fun invoke(xr: Query): Pair<Query, StatefulTransformer<Set<String>>> = trace("Uncapture $xr").andReturnIf {
    with(xr) {
      when {
        this is FlatMap && head.isUnaliased() ->
          invoke(id, body) { i, b -> FlatMap(head, i, b) }

        this is ConcatMap && head.isUnaliased() ->
          invoke(id, body) { i, b -> ConcatMap(head, i, b) }

        this is XR.Map && head.isUnaliased() ->
          invoke(id, body) { i, b -> Map(head, i, b) }

        this is Filter && head.isUnaliased() ->
          invoke(id, body) { i, b -> Filter(head, i, b) }

        this is GroupByMap && head.isUnaliased() -> {
          val (byComps, s1) = invoke(byAlias, byBody) { i, b -> i to b }
          val (byId1, byBody1) = byComps
          val (toComps, s2) = invoke(mapAlias, mapBody) { i, b -> i to b }
          val (toId1, mapBody1) = toComps
          (
            GroupByMap(head, byId1, byBody1, toId1, mapBody1) to
              Recurse(s1.state + s2.state + state)
            )
        }

        this is DistinctOn && head.isUnaliased() ->
          invoke(id, by) { i, b -> DistinctOn(head, i, b) }

        this is FlatMap ->
          recurseAndApply(head, id, body) { q, i, b -> FlatMap(q, i, b) }

        this is ConcatMap ->
          recurseAndApply(head, id, body) { q, i, b -> ConcatMap(q, i, b) }

        this is XR.Map ->
          recurseAndApply(head, id, body) { q, i, b -> Map(q, i, b) }

        this is Filter ->
          recurseAndApply(head, id, body) { q, i, b -> Filter(q, i, b) }

        this is GroupByMap -> {
          val (newHead, newState) = super.invoke(head)
          // Don't need to propagate state between these clauses since we are just refreshing aliases inside,
          // not recursively calling the AvoidAliasConflictl like in the head.isUnalised cases
          val (newByAlias, newByBody) = byAlias.refreshInsideOf(byBody) { "aliased-byBody-GroupByMap" }
          val (newMapAlias, newMapBody) = byAlias.refreshInsideOf(byBody) { "aliased-mapBody-GroupByMap" }
          (
            GroupByMap(newHead, newByAlias, newByBody, newMapAlias, newMapBody) to
              Recurse(newState.state + state)
            )
        }

        this is DistinctOn ->
          recurseAndApply(head, id, by) { q, i, b -> DistinctOn(q, i, b) }

        this is SortBy && head.isUnaliased() ->
          invoke(id, criteria) { i, c -> SortBy(head, i, c, ordering) }

        this is XR.FlatJoin -> {
          val (newHead, newState) = invoke(head)
          val (newId, newOnRaw) = id.refreshInsideOf(on) { "aliased-on-FlatJoin" }
          // Recurse into the on-clause to see if anything else needs to be de-aliased there
          val (newOn, newOnState) =
            trace("Uncapturing FlatJoin: Recurse with state: ${newState} + ${newId.name}").andReturnIf {
              AvoidAliasConflict(newState.state + newId.name, detemp, traceConfig)(newOnRaw)
            }({ it.first != newOnRaw })

          (FlatJoin(joinType, newHead, newId, newOn) to Recurse(newOnState.state + newState.state + state))
        }

        else -> super.invoke(xr)
      }
    }
  }({ it.first != xr })

//
//        case m @ DistinctOn(_, _, _) =>
//          recurseAndApply(m)(m => (m.query, m.alias, m.body))(DistinctOn(_, _, _))
//
//        case SortBy(Unaliased(q), x, p, o) =>
//          trace"Unaliased $qq uncapturing $x".andReturnIf {
//            apply(x, p)(SortBy(q, _, _, o))
//          }(_._1 != qq)
//
//        case FlatJoin(t, a, iA, o) =>
//          trace"Uncapturing FlatJoin $qq".andReturnIf {
//            val (ar, art) = apply(a)
//            val freshA    = freshIdent(iA)
//            val or =
//              trace"Uncapturing FlatJoin: Reducing $iA -> $freshA".andReturnIf {
//                BetaReduction(o, iA -> freshA)
//              }(_ != o)
//            val (orr, orrt) =
//              trace"Uncapturing FlatJoin: Recurse with state: ${art.state} + $freshA".andReturnIf {
//                AvoidAliasConflict(art.state + freshA.idName, detemp, traceConfig)(or)
//              }(_._1 != or)
//
//            (FlatJoin(t, ar, freshA, orr), orrt)
//          }(_._1 != qq)
//
//        case _: Entity | _: FlatMap | _: ConcatMap | _: Map | _: Filter | _: SortBy | _: GroupBy | _: Aggregation |
//            _: Take | _: Drop | _: Union | _: UnionAll | _: Distinct | _: DistinctOn | _: Nested =>
//          super.apply(qq)
//      }
//    }(_._1 != qq)


  /**
   * Create an fresh alias for this identifier and replace it in the given Query/Expression (i.e. `body`) with the new one.
   * Note: Technically we could get rid of the UNCHECKED_CAST suppression and make two versions of this function, once for XR.Expression. and one for XR.Query
   */
  @Suppress("UNCHECKED_CAST")
  fun <Body: XR> Ident.refreshInsideOf(body: Body, label: () -> String): Pair<XR.Ident, Body> {
    val id = this
    val fresh = freshIdent(id)
    val newBody =
      trace("RecurseAndApply ${label()} Replace: $id -> $fresh: ").andReturnIf {
        BetaReduction.ofXR(body, id to fresh)
      }({ it != body })
    return fresh to (newBody as Body)
  }

  /**
   * Typically we use this is the `head` is an Entity. What we want to do here is to de-alias
   * the idenifier in the body and then recurisvely apply the AvoidAliasConflict on the resulting body
   * in case other things inside there need dealising.
   * Note: Technically we could get rid of the UNCHECKED_CAST suppression and make two versions of this function, once for XR.Expression. and one for XR.Query
   */
  @Suppress("UNCHECKED_CAST")
  private inline operator fun <reified Q, Body: XR> invoke(x: Ident, body: Body, crossinline f: (Ident, Body) -> Q): Pair<Q, StatefulTransformer<Set<String>>> =
    trace("Uncapture Apply ($x, $body)").andReturnIf {
      val (fresh, newBodyRaw) = x.refreshInsideOf(body) { "unaliased-${Q::class.simpleName}" }
      val (newBody, t) =
        trace("Uncapture Apply Recurse").andReturnIf {
          AvoidAliasConflict(state + fresh.name, detemp, traceConfig)(newBodyRaw)
        }({ it.first != newBodyRaw })

      (f(fresh, newBody as Body) to t)
    }({ it.first != f(x, body) })


  /**
   * Typically we use this whent he `head` is something else that has an alias e.g. a `Map`. In this case we want to
   * recurisvley dealias the head and then dealias the `body`. Once we are doing that can just return the state.
   * It seems we don't need to recrusively dealias the `body` here, or at least cases where it causes an aliasing problem have not been found.
   *
   * Note: Technically we could get rid of the UNCHECKED_CAST suppression and make two versions of this function, once for XR.Expression. and one for XR.Query
   */
  @Suppress("UNCHECKED_CAST")
  private inline fun <reified Q, Head: XR, Body: XR> recurseAndApply(head: Head, x: Ident, body: Body, label: String = "", crossinline f: (Head, Ident, Body) -> Q): Pair<Q, StatefulTransformer<Set<String>>> =
    trace("Uncapture RecurseAndApply ($x, $body)").andReturnIf {
      val (newHead, newState) = super.invoke(head)
      val (fresh, newBody) = x.refreshInsideOf(body) { "aliased-${if (label != "") "$label-" else ""}-${Q::class.simpleName}" }
      (f(newHead as Head, fresh, newBody as Body) to AvoidAliasConflict(newState.state + fresh.name, detemp, traceConfig))
    }({ it.first != head })



// Scala
//  private def freshIdent(x: Ident, state: Set[IdentName] = state): Ident =
//    x match {
//      case TemporaryIdent(tid) if (detemp) =>
//        dedupeIdent(Ident("x", tid.quat), state)
//      case _ =>
//        dedupeIdent(x, state)
//    }

  private fun dedupeIdent(x: Ident, state: Set<String>): Ident {
    var i = 0
    var fresh = x
    while (state.contains(fresh.name)) {
      i++
      fresh = Ident("${x.name}$i", x.type, x.loc)
    }
    return fresh
  }


// Scala:
//  private def dedupeIdent(x: Ident, state: Set[IdentName] = state): Ident = {
//    def loop(x: Ident, n: Int): Ident = {
//      val fresh = Ident(s"${x.name}$n", x.quat)
//      if (!state.contains(fresh.idName))
//        fresh
//      else
//        loop(x, n + 1)
//    }
//    if (!state.contains(x.idName))
//      x
//    else
//      loop(x, 1)
//  }
}


fun Ident.isTemporary(): Boolean =
  name.matches("\\[tmp_[0-9A-Za-z]+\\]".toRegex())

