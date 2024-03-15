package io.exoquery.norm

import io.exoquery.xr.XR.*
import io.exoquery.xr.*
import io.decomat.*

class AdHocReduction {

  fun test() {
  }

  fun doMatch(q: Query): XR.Query? =
    on(q).match(
      // ---------------------------
      // *.filter

      // a.filter(b => c).filter(d => e) =>
      //    a.filter(b => c && e[d := b])
      //
      // Filter(Filter(a, b, c), d, e) =>
      case(Filter[Filter[Is(), Is()], Is()]).thenThis { (a, b, c), d, e ->
        val er = BetaReduction(e, d to b)
        Filter(a, b, XR.BinaryOp(c, BooleanOperator.and, er, loc), loc)
      },
      // ---------------------------
      // flatMap.*

      // a.flatMap(b => c).map(d => e) =>
      //    a.flatMap(b => c.map(d => e))
      //
      // Map(FlatMap(a, b, c), d, e) =>
      case(Map[FlatMap[Is(), Is()], Is()]).thenThis { (a, b, c), d, e ->
        FlatMap(a, b, Map(c, d, e, loc), loc)
      },
      // a.flatMap(b => c).filter(d => e) =>
      //    a.flatMap(b => c.filter(d => e))
      //
      // case Filter(FlatMap(a, b, c), d, e) =>
      case(Filter[FlatMap[Is(), Is()], Is()]).thenThis { (a, b, c), d, e ->
        FlatMap(a, b, Filter.cs(c, d, e), loc)
      },
      // a.flatMap(b => c.union(d))
      //    a.flatMap(b => c).union(a.flatMap(b => d))
      //
      // case FlatMap(a, b, Union(c, d)) =>
      case(FlatMap[Is(), Union[Is(), Is()]]).thenThis { a, b, (c, d) ->
        Union(FlatMap.cs(a, b, c), FlatMap.cs(a, b, d), loc)
      },
      // a.flatMap(b => c.unionAll(d))
      //    a.flatMap(b => c).unionAll(a.flatMap(b => d))
      //
      // case FlatMap(a, b, UnionAll(c, d)) =>
      case(FlatMap[Is(), UnionAll[Is(), Is()]]).thenThis { a, b, (c, d) ->
        UnionAll(FlatMap.cs(a, b, c), FlatMap.cs(a, b, d), loc)
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