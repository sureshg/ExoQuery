package io.exoquery.norm

import io.exoquery.*
import io.exoquery.util.TraceConfig
import io.exoquery.xr.XR
import io.exoquery.xr.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SymbolicReductionSpec: FreeSpec({

  fun symoblicReduction(xr: XR.Query) =
    SymbolicReduction(TraceConfig.empty).invoke(xr)?.let { replaceTempIdent(it) }

  "a.filter(b => c).flatMap(d => e.$)" - {
    "e is an entity"{
      val q = qr1.filter { b -> b.s == "s1" }.flatMap { d -> qr2 }
      val n = qr1.flatMap { d -> qr2.filter { x -> d.s == "s1" } }
      symoblicReduction(q.xr) shouldBe n.xr

// Scala:
//      val q = quote {
//        qr1.filter(b => b.s == "s1").flatMap(d => qr2)
//      }
//      val n = quote {
//        qr1.flatMap(d => qr2.filter(x => d.s == "s1"))
//      }
//      symbolicReduction(q.ast) mustEqual Some(n.ast)
    }
    "e isn't an entity" {
      val q = qr1.filter { b -> b.s == "s1" }.flatMap { d -> qr2.map { f -> f.s } }
      val n = qr1.flatMap { d -> qr2.filter { f -> d.s == "s1" }.map { f -> f.s } }
      symoblicReduction(q.xr) shouldBe n.xr

// Scala:
//      val q = quote {
//        qr1.filter(b => b.s == "s1").flatMap(d => qr2.map(f => f.s))
//      }
//      val n = quote {
//        qr1.flatMap(d => qr2.filter(f => d.s == "s1").map(f => f.s))
//      }
//      symbolicReduction(q.ast) mustEqual Some(n.ast)
    }
  }

  "a.flatMap(b => c).flatMap(d => e)" {
    val q = qr1.flatMap { b -> qr2 }.flatMap { d -> qr3 }
    val n = qr1.flatMap { b -> qr2.flatMap { d -> qr3 } }
    symoblicReduction(q.xr) shouldBe n.xr
  }

//
//  "a.flatMap(b => c).flatMap(d => e)" in {
//    val q = quote {
//      qr1.flatMap(b => qr2).flatMap(d => qr3)
//    }
//    val n = quote {
//      qr1.flatMap(b => qr2.flatMap(d => qr3))
//    }
//    symbolicReduction(q.ast) mustEqual Some(n.ast)
//  }

  "a.union(b).flatMap(c => d)" {
    val q = qr1.union(qr1.filter { t -> t.i == 1 }).flatMap { c -> qr2  }
    val n = qr1.flatMap { c -> qr2 }.union(qr1.filter { t -> t.i == 1 }.flatMap { c -> qr2 })
  }

// Scala:
//  "a.union(b).flatMap(c => d)" in {
//    val q = quote {
//      qr1.union(qr1.filter(t => t.i == 1)).flatMap(c => qr2)
//    }
//    val n = quote {
//      qr1.flatMap(c => qr2).union(qr1.filter(t => t.i == 1).flatMap(c => qr2))
//    }
//    symbolicReduction(q.ast) mustEqual Some(n.ast)
//  }

  "a.unionAll(b).flatMap(c => d)" {
    val q = qr1.unionAll(qr1.filter { t -> t.i == 1 }).flatMap { c -> qr2 }
    val n = qr1.flatMap { c -> qr2 }.unionAll(qr1.filter { t -> t.i == 1 }.flatMap { c -> qr2 })
    symoblicReduction(q.xr) shouldBe n.xr
  }

// Scala:
//  "a.unionAll(b).flatMap(c => d)" in {
//    val q = quote {
//      qr1.unionAll(qr1.filter(t => t.i == 1)).flatMap(c => qr2)
//    }
//    val n = quote {
//      qr1.flatMap(c => qr2).unionAll(qr1.filter(t => t.i == 1).flatMap(c => qr2))
//    }
//    symbolicReduction(q.ast) mustEqual Some(n.ast)
//  }

})