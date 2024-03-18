package io.exoquery.norm

import io.exoquery.xr.XR.*
import io.exoquery.xr.*
import io.decomat.*

object AdHocReduction {

  interface Foo {
    val x: XR.Query
  }

  interface Bar {
    val x: XR.FlatMap
  }

  fun invoke(q: Query): XR.Query? =
    on(q).match(
      // ---------------------------
      // *.filter

      // a.filter(b => c).filter(d => e) =>
      //    a.filter(b => c && e[d := b])
      //
      // Filter(Filter(a, b, c), d, e) =>
      case(Filter[Filter[Is(), Is()], Is()]).thenThis { (a, b, c), d, e ->
        val er = BetaReduction(e, d to b)
        Filter.cs(a, b, XR.BinaryOp(c, BooleanOperator.and, er))
      },
      // ---------------------------
      // flatMap.*

      // a.flatMap(b => c).map(d => e) =>
      //    a.flatMap(b => c.map(d => e))
      //
      // Map(FlatMap(a, b, c), d, e) =>
      case(Map[FlatMap[Is(), Is()], Is()]).then { (a, b, c), d, e ->
        FlatMap.csf(compLeft)(a, b, XR.Map.csf(comp)(c, d, e))
      },
      // a.flatMap(b => c).filter(d => e) =>
      //    a.flatMap(b => c.filter(d => e))
      //
      // case Filter(FlatMap(a, b, c), d, e) =>
      case(Filter[FlatMap[Is(), Is()], Is()]).then { (a, b, c), d, e ->
        FlatMap.csf(compLeft)(a, b, Filter.csf(comp)(c, d, e))
      },
      // a.flatMap(b => c.union(d))
      //    a.flatMap(b => c).union(a.flatMap(b => d))
      //
      // case FlatMap(a, b, Union(c, d)) =>
      case(FlatMap[Is(), Union[Is(), Is()]]).then { a, b, (c, d) ->
        Union.csf(compRight)(FlatMap.csf(comp)(a, b, c), FlatMap.csf(comp)(a, b, d))
      },
      // a.flatMap(b => c.unionAll(d))
      //    a.flatMap(b => c).unionAll(a.flatMap(b => d))
      //
      // case FlatMap(a, b, UnionAll(c, d)) =>
      case(FlatMap[Is(), UnionAll[Is(), Is()]]).then { a, b, (c, d) ->
        UnionAll.csf(compRight)(FlatMap.csf(comp)(a, b, c), FlatMap.csf(comp)(a, b, d))
      }
    )
}

/*
  def unapply(q: Query) =
    q match {

      // ---------------------------
      // *.filter

      // a.filter(b => c).filter(d => e) =>
      //    a.filter(b => c && e[d := b])
      case Filter(Filter(a, b, c), d, e) =>
        val er = BetaReduction(e, d -> b)
        Some(Filter(a, b, BinaryOperation(c, BooleanOperator.`&&`, er)))

      // ---------------------------
      // flatMap.*

      // a.flatMap(b => c).map(d => e) =>
      //    a.flatMap(b => c.map(d => e))
      case Map(FlatMap(a, b, c), d, e) =>
        Some(FlatMap(a, b, Map(c, d, e)))

      // a.flatMap(b => c).filter(d => e) =>
      //    a.flatMap(b => c.filter(d => e))
      case Filter(FlatMap(a, b, c), d, e) =>
        Some(FlatMap(a, b, Filter(c, d, e)))

      // a.flatMap(b => c.union(d))
      //    a.flatMap(b => c).union(a.flatMap(b => d))
      case FlatMap(a, b, Union(c, d)) =>
        Some(Union(FlatMap(a, b, c), FlatMap(a, b, d)))

      // a.flatMap(b => c.unionAll(d))
      //    a.flatMap(b => c).unionAll(a.flatMap(b => d))
      case FlatMap(a, b, UnionAll(c, d)) =>
        Some(UnionAll(FlatMap(a, b, c), FlatMap(a, b, d)))

      case other => None
    }
 */