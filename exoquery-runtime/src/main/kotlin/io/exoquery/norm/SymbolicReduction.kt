package io.exoquery.norm

import io.exoquery.util.TraceConfig
import io.exoquery.xr.BetaReduction
import io.exoquery.xr.XR
import io.exoquery.xr.cs

/**
 * This stage represents Normalization Stage1: Symbolic Reduction in Philip
 * Wadler's Paper "A Practical Theory of Language Integrated Query", given in
 * Figure 11.
 * https://homepages.inf.ed.ac.uk/slindley/papers/practical-theory-of-linq.pdf
 *
 * It represents foundational normalizations done to sequences that represents
 * queries. In Wadler's paper, he characterizes them as `for x in P ...``
 * whereas in Quill they are characterized as list comprehensions i.e.
 * `P.flatMap(x => ...)`.
 */
class SymbolicReduction(val traceConfig: TraceConfig) {
  fun invoke(q: XR.Query) =
    with(q) {
      when {
        /*
         * Represents if-for in Figure 11.
         * This transformation is particularity difficult to understand so I have added examples with several layers of granularity.
         * It's basic form goes like this:
         *
         * bottles.filter(bottle => isMerlot).flatMap(merlotBottle => {withCheese})
         *     bottles.flatMap(merlotBottle => {withCheese}.filter(_ => isMerlot[bottle := merlotBottle]))
         *
         * For example:
         *   The AST block withCheese is cheeses.filter(cheese => cheese.pairsWith == merlotBottle)
         *   The AST block isMerlot is bottle.isMerlot
         *
         * bottles.filter(bottle => bottle.isMerlot).flatMap(merlotBottle => {cheeses.filter(cheese => cheese.pairsWith == merlotBottle)} )
         *     bottles.flatMap(merlotBottle => cheeses.filter({bottle.isMerlot}[bottle := merlotBottle]).filter(cheese => cheese.pairsWith == merlotBottle)}
         * which is:
         *     bottles.flatMap(merlotBottle => cheeses.filter({doesnt-matter} => merlotBottle.isMerlot).filter(cheese => cheese.pairsWith == merlotBottle)
         *
         * a.filter(b => c).flatMap(d => e.$) =>
         *     a.flatMap(d => e.filter(_ => c[b := d]).$)
         *
         * case FlatMap(Filter(a, b, c), d, e: Query)
         *
         */
        this is XR.FlatMap && head is XR.Filter -> {
          val (a, b, c) = Triple(head.head, head.id, head.body)
          val (d, e) = Pair(id, body)
          val cr = BetaReduction(c, b to d)
          val er = with(head) {
            AttachToEntity({ q, id -> XR.Filter.cs(q, id, cr) })(e)
          }
          XR.FlatMap.cs(a, d, er)
        }

// Scala:
//      case FlatMap(Filter(a, b, c), d, e: Query) =>
//        val cr = BetaReduction(c, b -> d)
//        val er = AttachToEntity(Filter(_, _, cr))(e)
//        Some(FlatMap(a, d, er))

        // This transformation does not have an analogue in Wadler's paper, it represents the fundamental nature of the Monadic 'bind' function
        // that A.flatMap(a => B).flatMap(b => C) is isomorphic to A.flatMap(a => B.flatMap(b => C)).
        //
        // a.flatMap(b => c).flatMap(d => e) =>
        //     a.flatMap(b => c.flatMap(d => e))
        //
        // case FlatMap(FlatMap(a, b, c), d, e) =>
        //     Some(FlatMap(a, b, FlatMap(c, d, e)))
        this is XR.FlatMap && head is XR.FlatMap -> {
          val (a, b, c) = Triple(head.head, head.id, head.body)
          val (d, e) = Pair(id, body)
          XR.FlatMap.cs(a, b, XR.FlatMap.cs(c, d, e))
        }

// Scala:
//      case FlatMap(FlatMap(a, b, c), d, e) =>
//        Some(FlatMap(a, b, FlatMap(c, d, e)))

        // Represents for@ in Figure 11
        //
        // a.union(b).flatMap(c => d)
        //      a.flatMap(c => d).union(b.flatMap(c => d))
        //
        // case FlatMap(Union(a, b), c, d) =>
        //     Some(Union(FlatMap(a, c, d), FlatMap(b, c, d)))
        this is XR.FlatMap && head is XR.Union -> {
          val (a, b) = Pair(head.a, head.b)
          val (c, d) = Pair(id, body)
          with (head) {
            XR.Union.cs(XR.FlatMap.cs(a, c, d), XR.FlatMap.cs(b, c, d))
          }
        }

// Scala
//      case FlatMap(Union(a, b), c, d) =>
//        Some(Union(FlatMap(a, c, d), FlatMap(b, c, d)))

        // Represents for@ in Figure 11 (Wadler does not distinguish between Union and UnionAll)
        //
        // a.unionAll(b).flatMap(c => d)
        //      a.flatMap(c => d).unionAll(b.flatMap(c => d))
        this is XR.FlatMap && head is XR.UnionAll -> {
          val (a, b) = Pair(head.a, head.b)
          val (c, d) = Pair(id, body)
          with (head) {
            XR.UnionAll.cs(XR.FlatMap.cs(a, c, d), XR.FlatMap.cs(b, c, d))
          }
        }


      else -> null
    }
  }
}