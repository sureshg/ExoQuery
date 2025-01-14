package io.exoquery.norm

import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR
import io.exoquery.xr.*
import io.exoquery.xr.copy.*

data class Dealias(override val state: XR.Ident?, val traceConfig: TraceConfig): StatefulTransformer<Ident?> {
  val trace: Tracer =
    Tracer(TraceType.Standard, traceConfig, 1)

  override operator fun invoke(q: Query): Pair<Query, StatefulTransformer<Ident?>> =
    with(q) {
      when(this) {
//      case FlatMap(a, b, c) =>
//        dealias(a, b, c)(FlatMap) match {
//          case (FlatMap(a, b, c), _) =>
//            val (cn, cnt) = apply(c)
//            (FlatMap(a, b, cn), cnt)
//        }
        is FlatMap -> {
          val (a, b, c, _) = dealias(head, id, body)
          val (cn, cnt) = invoke(c) // need to recursively dealias this clause e.g. if it is a map-clause that has another alias inside
          FlatMap.cs(a, b, cn) to cnt
        }

//      case ConcatMap(a, b, c) =>
//        dealias(a, b, c)(ConcatMap) match {
//          case (ConcatMap(a, b, c), _) =>
//            val (cn, cnt) = apply(c)
//            (ConcatMap(a, b, cn), cnt)
//        }

        is ConcatMap -> {
          val (a, b, c, _) = dealias(head, id, body)
          val (cn, cnt) = invoke(c)
          ConcatMap.cs(a, b, cn) to cnt
        }

//      case Map(a, b, c) =>
//        dealias(a, b, c)(Map)

        is XR.Map -> {
          val (a, b, c, t) = dealias(head, id, body)
          Map.cs(a, b, c) to t
        }

//      case Filter(a, b, c) =>
//        dealias(a, b, c)(Filter)

        is Filter -> {
          val (a, b, c, t) = dealias(head, id, body)
          Filter.cs(a, b, c) to t
        }

//      case SortBy(a, b, c, d) =>
//        dealias(a, b, c)(SortBy(_, _, _, d))

        is SortBy -> {
          val (a, b, c, t) = dealias(head, id, criteria)
          SortBy.cs(a, b, c, ordering) to t
        }


//      case g @ GroupByMap(qry, b, c, d, e) =>
//        apply(qry) match {
//          case (an, t @ Dealias(Some(alias), traceConfig)) =>
//            val b1 = alias.copy(quat = b.quat)
//            val d1 = alias.copy(quat = d.quat)
//            (GroupByMap(an, b1, BetaReduction(c, b -> b1), d1, BetaReduction(e, d -> d1)), t)
//          case other =>
//            (g, Dealias(Some(b), traceConfig))
//        }

        is GroupByMap -> {
          val (an, t) = invoke(head)
          val alias = t.state
          when {
            alias != null -> {
              val byAlias1 = alias.copy(type = byAlias.type)
              val mapAlias1 = alias.copy(type = mapAlias.type)
              val byBody1 = BetaReduction(byBody, byAlias to byAlias1)
              val mapBody1 = BetaReduction(mapBody, mapAlias to mapAlias1)
              GroupByMap(an, byAlias1, byBody1, mapAlias1, mapBody1) to t
            }
            else -> {
              this to Dealias(byAlias, traceConfig)
            }
          }
        }

//      case DistinctOn(a, b, c) =>
//        dealias(a, b, c)(DistinctOn)

        is DistinctOn -> {
          val (a, b, c, t) = dealias(head, id, by)
          DistinctOn.cs(a, b, c) to t
        }

//      case Take(a, b) =>
//        val (an, ant) = apply(a)
//        (Take(an, b), ant)

        is Take -> {
          val (an, ant) = invoke(head)
          Take.cs(an, num) to ant
        }

//      case Drop(a, b) =>
//        val (an, ant) = apply(a)
//        (Drop(an, b), ant)

        is Drop -> {
          val (an, ant) = invoke(head)
          Drop.cs(an, num) to ant
        }

//      case Union(a, b) =>
//        val (an, _) = apply(a)
//        val (bn, _) = apply(b)
//        (Union(an, bn), Dealias(None, traceConfig))

        is Union -> {
          val (an, _) = invoke(a)
          val (bn, _) = invoke(b)
          Union.cs(an, bn) to Dealias(null, traceConfig)
        }

//      case UnionAll(a, b) =>
//        val (an, _) = apply(a)
//        val (bn, _) = apply(b)
//        (UnionAll(an, bn), Dealias(None, traceConfig))

        is UnionAll -> {
          val (an, _) = invoke(a)
          val (bn, _) = invoke(b)
          UnionAll.cs(an, bn) to Dealias(null, traceConfig)
        }

//      case FlatJoin(t, a, iA, o) =>
//        val ((an, iAn, on), ont) = dealias(a, iA, o)((_, _, _))
//        (FlatJoin(t, an, iAn, on), Dealias(Some(iA), traceConfig))

        is FlatJoin -> {
          val (head1, id1, on1) = dealias(head, id, on)
          FlatJoin.cs(head1, id1, on1) to Dealias(id1, traceConfig)
        }

//      case _: Entity | _: Distinct | _: Nested =>
//        (q, Dealias(None, traceConfig))

        is Entity, is Distinct, is Nested, is FlatFilter, is FlatSortBy, is FlatGroupBy, is Infix, is QueryOf, is TagForSqlQuery -> {
          this to Dealias(null, traceConfig)
        }
      }
  }

  data class DealiasResultA(val a: Query, val b: Ident, val c: Expression, val newState: StatefulTransformer<Ident?>)
  data class DealiasResultB(val a: Query, val b: Ident, val c: Query, val newState: StatefulTransformer<Ident?>)

  private fun dealias(a: Query, b: Ident, c: Expression): DealiasResultA {
    val (an, t) = invoke(a)
    val alias = t.state
    return when {
      alias != null -> {
        val retypedAlias = alias.copy(type = b.type)
        trace("Dealias (Q/Expr) $b into $retypedAlias").andLog()
        DealiasResultA(an, retypedAlias, BetaReduction(c, b to retypedAlias), t)
      }
      else ->
        DealiasResultA(a, b, c, Dealias(b, traceConfig))
    }
  }

  private fun dealias(a: Query, b: Ident, c: Query): DealiasResultB {
    val (an, t) = invoke(a)
    val alias = t.state
    return when {
      alias != null -> {
        val retypedAlias = alias.copy(type = b.type)
        trace("Dealias (Q/Q) $b into $retypedAlias").andLog()
        DealiasResultB(an, retypedAlias, BetaReduction.ofQuery(c, b to retypedAlias), t)
      }
      else ->
        DealiasResultB(a, b, c, Dealias(b, traceConfig))
    }
  }


//  private def dealias[T](a: Ast, b: Ident, c: Ast)(f: (Ast, Ident, Ast) => T) =
//    apply(a) match {
//      case (an, t @ Dealias(Some(alias), traceConfig)) =>
//        val retypedAlias = alias.copy(quat = b.quat)
//        trace"Dealias $b into $retypedAlias".andLog()
//        (f(an, retypedAlias, BetaReduction(c, b -> retypedAlias)), t)
//      case other =>
//        (f(a, b, c), Dealias(Some(b), traceConfig))
//    }
}

//object Dealias {
//  def apply(query: Query)(traceConfig: TraceConfig) =
//    new Dealias(None, traceConfig)(query) match {
//      case (q, _) => q
//    }
//}

class DealiasApply(val traceConfig: TraceConfig) {
  operator fun invoke(query: Query): Query =
    Dealias(null, traceConfig)(query).first
}

//class DealiasApply(traceConfig: TraceConfig) {
//  def apply(query: Query) =
//    new Dealias(None, traceConfig)(query) match {
//      case (q, _) => q
//    }
//}
