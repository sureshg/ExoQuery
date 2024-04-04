package io.exoquery.norm

import io.exoquery.util.TraceConfig
import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.kotest.matchers.shouldBe

class AdHocReductionSpec: FreeSpec({

  val AdHocReduction = AdHocReduction(TraceConfig.empty)

  "*.filter" - {
    "simple" {
      val q = qr1.filter { b -> b.s == "s1" }.filter { d -> d.s == "s2" }
      val n = qr1.filter { b -> b.s == "s1" && b.s == "s2" }
      AdHocReduction(q.xr) shouldBe n.xr
    }
    "and - combo" {
      val q = qr1.filter { b -> b.s == "s1" && b.s == "s2" }.filter { d -> d.s == "s3" }
      val n = qr1.filter { b -> b.s == "s1" && b.s == "s2" && b.s == "s3" }
      AdHocReduction(q.xr) shouldBe n.xr
    }
    "or - combo" {
      val q = qr1.filter { b -> b.s == "s1" || b.s == "s11" }.filter { d -> d.s == "s2" }
      val n = qr1.filter { b -> (b.s == "s1" || b.s == "s11") && b.s == "s2" }
      AdHocReduction(q.xr) shouldBe n.xr
    }
  }

// Scala
//  "*.filter" - {
//    "a.filter(b => c).filter(d => e)" in {
//      val q = quote {
//        qr1.filter(b => b.s == "s1").filter(d => d.s == "s2")
//      }
//      val n = quote {
//        qr1.filter(b => b.s == "s1" && b.s == "s2")
//      }
//      AdHocReduction.unapply(q.ast) mustEqual Some(n.ast)
//    }
//  }

  "flatMap.*" - {
    "a.flatMap(b => c).map(d => e)" {
      val q = qr1.flatMap { b -> qr2 }.map { d -> d.s }
      val n = qr1.flatMap { b -> qr2.map { d -> d.s } }
      AdHocReduction(q.xr) shouldBe n.xr
    }

//  "flatMap.*" - {
//    "a.flatMap(b => c).map(d => e)" in {
//      val q = quote {
//        qr1.flatMap(b => qr2).map(d => d.s)
//      }
//      val n = quote {
//        qr1.flatMap(b => qr2.map(d => d.s))
//      }
//      AdHocReduction.unapply(q.ast) mustEqual Some(n.ast)
//    }

  "a.flatMap(b => c).filter(d => e)" {
    val q = qr1.flatMap { b -> qr2 }.filter { d -> d.s == "s2" }
    val n = qr1.flatMap { b -> qr2.filter { d -> d.s == "s2" } }
    AdHocReduction(q.xr) shouldBe n.xr
  }

//    "a.flatMap(b => c).filter(d => e)" in {
//      val q = quote {
//        qr1.flatMap(b => qr2).filter(d => d.s == "s2")
//      }
//      val n = quote {
//        qr1.flatMap(b => qr2.filter(d => d.s == "s2"))
//      }
//      AdHocReduction.unapply(q.ast) mustEqual Some(n.ast)
//    }


    "a.flatMap(b => c.union(d))" {
      val q = qr1.flatMap { b -> qr2.filter { t -> t.i == 1 }.union(qr2.filter { t -> t.s == "s" }) }
      val n = qr1.flatMap { b -> qr2.filter { t -> t.i == 1 } }.union(qr1.flatMap { b -> qr2.filter { t -> t.s == "s" } })
      AdHocReduction(q.xr) shouldBe n.xr
    }

//    "a.flatMap(b => c.union(d))" in {
//      val q = quote {
//        qr1.flatMap(b => qr2.filter(t => t.i == 1).union(qr2.filter(t => t.s == "s")))
//      }
//      val n = quote {
//        qr1.flatMap(b => qr2.filter(t => t.i == 1)).union(qr1.flatMap(b => qr2.filter(t => t.s == "s")))
//      }
//      AdHocReduction.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "a.flatMap(b => c.unionAll(d))" {
      val q = qr1.flatMap { b -> qr2.filter { t -> t.i == 1 }.unionAll(qr2.filter { t -> t.s == "s" }) }
      val n = qr1.flatMap { b -> qr2.filter { t -> t.i == 1 } }.unionAll(qr1.flatMap { b -> qr2.filter { t -> t.s == "s" } })
      AdHocReduction(q.xr) shouldBe n.xr
    }

//    "a.flatMap(b => c.unionAll(d))" in {
//      val q = quote {
//        qr1.flatMap(b => qr2.filter(t => t.i == 1).unionAll(qr2.filter(t => t.s == "s")))
//      }
//      val n = quote {
//        qr1.flatMap(b => qr2.filter(t => t.i == 1)).unionAll(qr1.flatMap(b => qr2.filter(t => t.s == "s")))
//      }
//      AdHocReduction.unapply(q.ast) mustEqual Some(n.ast)
//    }
//  }

  }
})