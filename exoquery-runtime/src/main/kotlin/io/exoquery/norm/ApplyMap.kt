package io.exoquery.norm

import io.decomat.*
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.CollectXR
import io.exoquery.xr.XR
import io.exoquery.xr.XR.*
import io.exoquery.xr.*

class ApplyMap(val traceConfig: TraceConfig) {

  val trace: Tracer =
    Tracer(TraceType.AvoidAliasConflict, traceConfig, 1)

  companion object {
    fun Expression.hasImpureInfix() = // TODO Optimize with ContainsXR
      CollectXR.invoke(this) {
        when {
          this is Infix && this.pure == false -> this
          else -> null
        }
      }.isNotEmpty()

    fun Expression.hasImpurities() = // TODO Optimize with ContainsXR
      CollectXR.invoke(this) {
        when {
          this is Infix && this.pure == false -> this
          this is Aggregation -> this
          else -> null
        }
      }.isNotEmpty()
  }

  object MapWithoutInfixes {
    operator fun <AP: Pattern<XR.Query>, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
      customPattern2M(x, y) { it: XR.Query ->
        with(it) {
          when {
            this is XR.Map && body.hasImpureInfix() -> null
            this is XR.Map -> Components2M(head, id, body)
            else -> null
          }
        }
      }
  }

  object DetachableMap {
    operator fun <AP: Pattern<XR.Query>, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
      customPattern2M(x, y) { it: XR.Map ->
        with(it) {
          when {
            body.hasImpurities() -> null
            head is DistinctOn -> null
            head is FlatJoin -> null
            body.hasImpureInfix() -> null
            else -> Components2M(head, id, body)
          }
        }
      }
  }


// Scala
//  object DetachableMap {
//    def unapply(ast: Ast): Option[(Ast, Ident, Ast)] =
//      ast match {
//        // Maps that contains
//        case Map(_, _, HasImpurities())    => None
//        case Map(a: DistinctOn, b, c)           => None
//        case Map(a: FlatJoin, b, c)             => None // FlatJoin should always be surrounded by a Map
//        case Map(a, b, InfixedTailOperation(c)) => None
//        case Map(a, b, c)                       => Some((a, b, c))
//        case _                                  => None
//      }
//  }



  operator fun invoke(q: Query): Query? =
    on(q).match(
//      case Map(a: GroupByMap, b, c) if (b == c) => None
//      case Map(a: Nested, b, c) if (b == c)     => None
//      case Map(a: FlatJoin, b, c) if (b == c)   => None // FlatJoin should always be surrounded by a Map
//      case Nested(DetachableMap(a, b, c))       => None

      case(XR.Map[Is<GroupByMap>(), Is()]).then { _, _, _ -> null },
      case(XR.Map[Is<Nested>(), Is()]).then { _, _, _ -> null },
      case(XR.Map[Is<FlatJoin>(), Is()]).then { _, _, _ -> null },
      case(Nested[DetachableMap[Is(), Is()]]).then { _ -> null }, // Maybe just Nested[XR.Map[...]]


//  map(i => (i.i, i.l)).distinct.map(x => (x._1, x._2)) =>
//    map(i => (i.i, i.l)).distinct
//      case Map(Distinct(DetachableMap(a, b, c)), d, e) if isomorphic(e, c, d) =>
//        trace"ApplyMap on Distinct for $q" andReturn Some(Distinct(Map(a, b, c)))

      //  map(i => (i.i, i.l)).distinct.map(x => (x._1, x._2)) =>
      //    map(i => (i.i, i.l)).distinct
      case(XR.Map.DistinctHead[DetachableMap[Is(), Is()], Is()]).thenThis { (a, b, c), d, e ->
        trace("ApplyMap on Distinct for $q") andReturn { Distinct(XR.Map.cs(a, b, c), loc) }
      },

      // When dealing with a map inside of a map the rules for detachability are more lienent
      // i.e. they can contain DistinctOn and FlatJoin because these inside elements can just be
      // compressed down to the inner map. The only case where this is not true is where
      // there is an infix with an impure element inside.
      // a.map(b => c).map(d => e) =>
      //    a.map(b => e[d := c])
      case(XR.Map[MapWithoutInfixes[Is(), Is()], Is()]).thenThis { (a, b, c), d, e ->
        val er = BetaReduction(e, d to c)
        trace("ApplyMap on double-map for $q") andReturn { XR.Map.cs(a, b, er) }
      },

//      a.map(b => c).map(d => e) =>
//        a.map(b => e[d := c])
//      case before @ Map(MapWithoutInfixes(a, b, c), d, e) =>
//        val er = BetaReduction(e, d -> c)
//        trace"ApplyMap on double-map for $q" andReturn Some(Map(a, b, er))

      // a.map(b => b) =>
      //    a
      case(XR.Map[Is<Query>(), Is()]).then { a, b, c -> if (b == c) a else null },

//      // a.map(b => b) =>
//      //    a
//      case Map(a: Query, b, c) if (b == c) =>
//        trace"ApplyMap on identity-map for $q" andReturn Some(a)

      // a.map(b => c).flatMap(d => e) =>
      //    a.flatMap(b => e[d := c])
      case(XR.FlatMap[DetachableMap[Is(), Is()], Is()]).thenThis { (a, b, c), d, e ->
        val er = BetaReduction.ofQuery(e, d to c)
        trace("ApplyMap inside flatMap for $q") andReturn { XR.FlatMap.cs(a, b, er) }
      },

//      // a.map(b => c).flatMap(d => e) =>
//      //    a.flatMap(b => e[d := c])
//      case FlatMap(DetachableMap(a, b, c), d, e) =>
//        val er = BetaReduction(e, d -> c)
//        trace"ApplyMap inside flatMap for $q" andReturn Some(FlatMap(a, b, er))

      // a.map(b => c).filter(d => e) =>
      //    a.filter(b => e[d := c]).map(b => c)
      case(XR.Filter[DetachableMap[Is(), Is()], Is()]).then { (a, b, c), d, e ->
        val er = BetaReduction(e, d to c)
        trace("ApplyMap inside filter for $q") andReturn { XR.Map.csf(XR.Filter.csf(a, b, er)(comp), b, c)(compLeft) }
      },

//      // a.map(b => c).filter(d => e) =>
//      //    a.filter(b => e[d := c]).map(b => c)
//      case Filter(DetachableMap(a, b, c), d, e) =>
//        val er = BetaReduction(e, d -> c)
//        trace"ApplyMap inside filter for $q" andReturn Some(Map(Filter(a, b, er), b, c))

      // a.map(b => c).sortBy(d => e) =>
      //    a.sortBy(b => e[d := c]).map(b => c)
      case(XR.SortBy[DetachableMap[Is(), Is()], Is()]).then { (a, b, c), d, e ->
        val er = BetaReduction(e, d to c)
        trace("ApplyMap inside sortBy for $q") andReturn { XR.Map.csf(XR.SortBy.csf(a, b, er, comp.ordering)(comp), b, c)(compLeft) }
      },

      // TODO This is not in Quill. Is it valid?
      // a.map(b => c).distinctOn(d => e) =>
      //    a.distinctOn(b => e[d := c]).map(b => c)
      // case(XR.DistinctOn[DetachableMap[Is(), Is()], Is()]).then { (a, b, c), d, e ->
      //   val er = BetaReduction(e, d to c)
      //   trace("ApplyMap inside sortBy for $q") andReturn { XR.Map.csf(XR.DistinctOn.csf(a, b, er)(comp), b, c)(compLeft) }
      // },

//      // a.map(b => c).sortBy(d => e) =>
//      //    a.sortBy(b => e[d := c]).map(b => c)
//      case SortBy(DetachableMap(a, b, c), d, e, f) =>
//        val er = BetaReduction(e, d -> c)
//        trace"ApplyMap inside sortBy for $q" andReturn Some(Map(SortBy(a, b, er, f), b, c))

      // a.map(b => c).sortBy(d => e).distinct =>
      //    a.sortBy(b => e[d := c]).map(b => c).distinct
      case(XR.SortBy.DistinctHead[DetachableMap[Is(), Is()], Is()]).then { (a, b, c), d, e ->
        val er = BetaReduction(e, d to c)
        trace("ApplyMap inside sortBy+distinct for $q") andReturn { XR.Distinct(XR.Map.csf(XR.SortBy.csf(a, b, er, comp.ordering)(comp), b, c)(compLeft)) }
      },


//      // a.map(b => c).sortBy(d => e).distinct =>
//      //    a.sortBy(b => e[d := c]).map(b => c).distinct
//      case SortBy(Distinct(DetachableMap(a, b, c)), d, e, f) =>
//        val er = BetaReduction(e, d -> c)
//        trace"ApplyMap inside sortBy+distinct for $q" andReturn Some(Distinct(Map(SortBy(a, b, er, f), b, c)))


      // === Conceptual Example (same as for groupBy.map) ===
      // Instead of transforming spirit into gin and the bottling the gin, bottle the
      // spirit first, then have the spirit transform into gin inside of the bottles.
      // (The only difference between this and groupByMap is that we have two kinds of bottles: A and B)
      //
      // spirits.map(spirit => ginifySpirit).groupByMap(gin => bottleGinA)(gin => bottleGinB) =>
      //    spirits.groupByMap(spirit => bottleGinA[gin := ginifySpirit])(spirit => bottleGinB[gin := ginifySpirit])

      // a.map(b => c).groupByMap(d => e)(d => f) =>
      //    a.groupByMap(b => e[d := c])(b => f[d := c])
      // where d := d1
      // ============== Alternatively: ==============
      // a.map(b => c).groupByMap(byAlias => byBody)(mapAlias => mapBody) =>
      //    a.groupByMap(b => byBody[byAlias := c])(b => mapBody[mapAlias := c])
      // where byAlias := mapAlias
      case(XR.GroupByMap[DetachableMap[Is(), Is()], Is()]).thenThis { (a, b, c), _ ->
        val d = byAlias
        val e = byBody
        val d1 = mapAlias
        val f = mapBody

        val er  = BetaReduction(e, d to c)
        val fr  = BetaReduction(f, d1 to c)
        val grp = XR.GroupByMap.cs(a, b, er, b, fr)
        trace("ApplyMap inside groupByMap for $q") andReturn { grp }
      },


//      // a.map(b => c).groupByMap(d => e)(d => f) =>
//      //    a.groupByMap(b => e[d := c])(b => f[d := c])
//      // where d := d1
//      case GroupByMap(DetachableMap(a, b, c), d, e, d1, f) =>
//        val er  = BetaReduction(e, d -> c)
//        val fr  = BetaReduction(f, d1 -> c)
//        val grp = GroupByMap(a, b, er, b, fr)
//        trace"ApplyMap inside groupByMap for $q" andReturn Some(grp)

      // a.map(b => c).drop(d) =>
      //    a.drop(d).map(b => c)
      case(XR.Drop[DetachableMap[Is(), Is()], Is()]).then { (a, b, c), d ->
        trace("ApplyMap inside drop for $q") andReturn { XR.Map.csf(XR.Drop.csf(a, d)(comp), b, c)(compLeft) }
      },

//      // a.map(b => c).drop(d) =>
//      //    a.drop(d).map(b => c)
//      case Drop(DetachableMap(a, b, c), d) =>
//        trace"ApplyMap inside drop for $q" andReturn Some(Map(Drop(a, d), b, c))

      // a.map(b => c).take(d) =>
      //    a.drop(d).map(b => c)
      case(XR.Take[DetachableMap[Is(), Is()], Is()]).then { (a, b, c), d ->
        trace("ApplyMap inside take for $q") andReturn { XR.Map.csf(XR.Take.csf(a, d)(comp), b, c)(compLeft) }
      },

//      // a.map(b => c).take(d) =>
//      //    a.drop(d).map(b => c)
//      case Take(DetachableMap(a, b, c), d) =>
//        trace"ApplyMap inside take for $q" andReturn Some(Map(Take(a, d), b, c))

      // a.map(b => c).nested =>
      //    a.nested.map(b => c)
      case(XR.Nested[DetachableMap[Is(), Is()]]).then { (a, b, c) ->
        trace("ApplyMap inside nested for $q") andReturn { XR.Map.csf(XR.Nested.csf(a)(comp), b, c)(compInner) }
      },

//      // a.map(b => c).nested =>
//      //    a.nested.map(b => c)
//      case Nested(DetachableMap(a, b, c)) =>
//        trace"ApplyMap inside nested for $q" andReturn Some(Map(Nested(a), b, c))
    )

}