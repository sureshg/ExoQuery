package io.exoquery.norm

import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.exoquery.util.TraceConfig
import io.kotest.matchers.shouldBe

class ApplyMapSpec: FreeSpec({
  val ApplyMap = ApplyMap(TraceConfig.empty)

  "applies intermediate map" - {
    "flatMap" {
      val q = qr1.map { y -> y.s }.flatMap { s -> qr2.filter { z -> z.s == s } }
      val n = qr1.flatMap { y -> qr2.filter { z -> z.s == y.s } }
      ApplyMap(q.xr) shouldBe n.xr
    }

//    "flatMap" in {
//      val q = quote {
//        qr1.map(y => y.s).flatMap(s => qr2.filter(z => z.s == s))
//      }
//      val n = quote {
//        qr1.flatMap(y => qr2.filter(z => z.s == y.s))
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "filter" {
      val q = qr1.map { y -> y.s }.filter { s -> s == "s" }
      val n = qr1.filter { y -> y.s == "s" }.map { y -> y.s }
      ApplyMap(q.xr) shouldBe n.xr
    }

//    "filter" in {
//      val q = quote {
//        qr1.map(y => y.s).filter(s => s == "s")
//      }
//      val n = quote {
//        qr1.filter(y => y.s == "s").map(y => y.s)
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "map" {
      val q = qr1.map { y -> y.s }.map { s -> s }
      val n = qr1.map { y -> y.s }
      ApplyMap(q.xr) shouldBe n.xr
    }

//    "map" in {
//      val q = quote {
//        qr1.map(y => y.s).map(s => s)
//      }
//      val n = quote {
//        qr1.map(y => y.s)
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "sortBy" {
      val q = qr1.map { y -> y.s }.sortedBy { s -> s }
      val n = qr1.sortedBy { y -> y.s }.map { y -> y.s }
      ApplyMap(q.xr) shouldBe n.xr
    }

//    "sortBy" in {
//      val q = quote {
//        qr1.map(y => y.s).sortBy(s => s)
//      }
//      val n = quote {
//        qr1.sortBy(y => y.s).map(y => y.s)
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "identity map (behind a sort-by)" {
      val q = qr1.sortedBy { y -> y.s }.map { y -> y }
      val n = qr1.sortedBy { y -> y.s }
      ApplyMap(q.xr) shouldBe n.xr
    }

//    "identity map" in {
//      val q = quote {
//        qr1.sortBy(y => y.s).map(y => y)
//      }
//      val n = quote {
//        qr1.sortBy(y => y.s)
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "distinct" {
      val q = qr1.map { i -> i.i to i.l }.distinct().map { x -> x.first to x.second }
      val n = qr1.map { i -> i.i to i.l }.distinct()
      ApplyMap(q.xr) shouldBe n.xr
    }

// Sclaa:
//    "distinct" in {
//      val q = quote {
//        query[TestEntity].map(i => (i.i, i.l)).distinct.map(x => (x._1, x._2))
//      }
//      val n = quote {
//        query[TestEntity].map(i => (i.i, i.l)).distinct
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "distinct + sort" {
      val q = qr1.map { i -> i.i to i.l }.distinct().sortedBy { x -> x.first }
      val n = qr1.sortedBy { i -> i.i }.map { i -> i.i to i.l }.distinct()
      ApplyMap(q.xr) shouldBe n.xr
    }

// Scala:
//    "distinct + sort" in {
//      val q = quote {
//        query[TestEntity].map(i => (i.i, i.l)).distinct.sortBy(_._1)
//      }
//      val n = quote {
//        query[TestEntity].sortBy(i => i.i).map(i => (i.i, i.l)).distinct
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "take" {
      val q = qr1.map { y -> y.s }.take(1)
      val n = qr1.take(1).map { y -> y.s }
      ApplyMap(q.xr) shouldBe n.xr
    }

//    "take" in {
//      val q = quote {
//        qr1.map(y => y.s).take(1)
//      }
//      val n = quote {
//        qr1.take(1).map(y => y.s)
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "drop" {
      val q = qr1.map { y -> y.s }.drop(1)
      val n = qr1.drop(1).map { y -> y.s }
      ApplyMap(q.xr) shouldBe n.xr
    }

//    "drop" in {
//      val q = quote {
//        qr1.map(y => y.s).drop(1)
//      }
//      val n = quote {
//        qr1.drop(1).map(y => y.s)
//      }
//      ApplyMap.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "nested" {
      val q = qr1.map { y -> y.s }.nested()
      // I.e. apply-map should not change anything in a nested query. The original structure should be preserved
      // since nesting a query should isolate everything inside the nesting.
      // For example this:
      //   ApplyMap(query[Person].nested.map(p => p.age + 1)
      // Should NOT reduce to this:
      //   ApplyMap(query[Person].map(p => p.age + 1).nested
      // The original query should remain unaltered.
      // That is the semantic meaning of `nested` and things like impure-infixes rely on that.
      // (e.g. if the impure-infix is a partition-by it needs to remain as-is and not be moved around the query).
      ApplyMap(q.xr) shouldBe null
    }

//    "nested" in {
//      val q = quote {
//        qr1.map(y => y.s).nested
//      }
//      // I.e. apply-map should not change anything in a nested query. The original structure should be preserved
//      // since nesting a query should isolate everything inside the nesting.
//      // For example this:
//      //   ApplyMap(query[Person].nested.map(p => p.age + 1)
//      // Should NOT reduce to this:
//      //   ApplyMap(query[Person].map(p => p.age + 1).nested
//      // The original query should remain unaltered.
//      // That is the semantic meaning of `nested` and things like impure-infixes rely on that.
//      // (e.g. if the impure-infix is a partition-by it needs to remain as-is and not be moved around the query).
//      ApplyMap.unapply(q.ast) mustEqual None
//    }
  }

})