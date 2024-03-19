package io.exoquery.norm

import io.exoquery.xr.StatelessTransformer
import io.exoquery.xr.*
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR

class NormalizeNestedStructures(val normalize: StatelessTransformer) {

  fun Query.nullIfSameAs(q: Query) =
    if (this == q) null else this

  fun invoke(q: Query): Query? =
    with(q) {
      when(this) {
        is Entity -> null
        is XR.Map -> XR.Map.cs(normalize(head), id, normalize(body)).nullIfSameAs(q)
//      case Map(a, b, c)       => apply(a, c)(Map(_, b, _))

        is FlatMap -> FlatMap.cs(normalize(head), id, normalize(body)).nullIfSameAs(q)
//      case FlatMap(a, b, c)   => apply(a, c)(FlatMap(_, b, _))

        is ConcatMap -> ConcatMap.cs(normalize(head), id, normalize(body)).nullIfSameAs(q)
//      case ConcatMap(a, b, c) => apply(a, c)(ConcatMap(_, b, _))

        is Filter -> Filter.cs(normalize(head), id, normalize(body)).nullIfSameAs(q)
//      case Filter(a, b, c)    => apply(a, c)(Filter(_, b, _))

        is SortBy -> SortBy.cs(normalize(head), id, normalize(criteria), ordering).nullIfSameAs(q)
//      case SortBy(a, b, c, d) => apply(a, c)(SortBy(_, b, _, d))

        is GroupByMap -> GroupByMap.cs(normalize(head), byAlias, normalize(byBody), mapAlias, normalize(mapBody)).nullIfSameAs(q)
//      case GroupByMap(a, b, c, d, e) =>
//        (normalize(a), normalize(c), normalize(e)) match {
//          case (`a`, `c`, `e`) => None
//          case (a, c, e)       => Some(GroupByMap(a, b, c, d, e))
//        }

        is Take -> Take.cs(normalize(head), normalize(num)).nullIfSameAs(q)
//      case Take(a, b)          => apply(a, b)(Take.apply)

        is Drop -> Drop.cs(normalize(head), normalize(num)).nullIfSameAs(q)
//      case Drop(a, b)          => apply(a, b)(Drop.apply)

        is Union -> Union.cs(normalize(a), normalize(b)).nullIfSameAs(q)
//      case Union(a, b)         => apply(a, b)(Union.apply)

        is UnionAll -> UnionAll.cs(normalize(a), normalize(b)).nullIfSameAs(q)
//      case UnionAll(a, b)      => apply(a, b)(UnionAll.apply)

        is Distinct -> Distinct.cs(normalize(head)).nullIfSameAs(q)
//      case Distinct(a)         => apply(a)(Distinct.apply)

        is DistinctOn -> DistinctOn.cs(normalize(head), id, normalize(by)).nullIfSameAs(q)
//      case DistinctOn(a, b, c) => apply(a, c)(DistinctOn(_, b, _))

        is Nested -> Nested.cs(normalize(head)).nullIfSameAs(q)
//      case Nested(a)           => apply(a)(Nested.apply)


        is FlatJoin -> FlatJoin.cs(normalize(head), id, normalize(on)).nullIfSameAs(q)
//      case FlatJoin(t, a, iA, on) =>
//        (normalize(a), normalize(on)) match {
//          case (`a`, `on`) => None
//          case (a, on)     => Some(FlatJoin(t, a, iA, on))
//        }
//    }

        is FlatFilter -> FlatFilter.cs(normalize(by)).nullIfSameAs(q)
        is FlatGroupBy -> FlatGroupBy.cs(normalize(by)).nullIfSameAs(q)
        is FlatSortBy -> FlatSortBy.cs(normalize(by), ordering).nullIfSameAs(q)
        // Not sure why Quill didn't normalize Infixes (maybe it didn't matter because normalization is mainly for XR.Query)
        is Infix -> Infix.cs(parts, params.map { normalize(it) }).nullIfSameAs(q)
        is Marker -> Marker.cs(name, expr?.let { normalize(it) }).nullIfSameAs(q)
        is RuntimeQueryBind -> null
      }
    }




//
//  private def apply(a: Ast)(f: Ast => Query) =
//    (normalize(a)) match {
//      case (`a`) => None
//      case (a)   => Some(f(a))
//    }
//
//  private def apply(a: Ast, b: Ast)(f: (Ast, Ast) => Query) =
//    (normalize(a), normalize(b)) match {
//      case (`a`, `b`) => None
//      case (a, b)     => Some(f(a, b))
//    }

}