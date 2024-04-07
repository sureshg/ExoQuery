package io.exoquery.norm

import io.exoquery.util.TraceConfig
import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.kotest.matchers.shouldBe

class OrderTermsSpec: FreeSpec({
  val OrderTerms = OrderTerms(TraceConfig.empty)

  "doesn't reorder sortedBy.map" {
    val q = qr1.sortedBy { b -> b.s }.map { b -> b }
    OrderTerms(q.xr) shouldBe null
  }

//
//  "doesn't reorder groupBy.map" in {
//    val q = quote {
//      qr1.map(b => b.s).sortBy(b => b)
//    }
//    OrderTerms.unapply(q.ast) mustEqual None
//  }

  "sortedBy" - {
    "a.sortedBy(b => c).filter(d => e)" {
      val q = qr1.sortedBy { b -> b.s }.filter { d -> d.s == "s1" }
      println(q.xr.showRaw())
      val n = qr1.filter { d -> d.s == "s1" }.sortedBy { b -> b.s }
      println(n.xr.showRaw())
      println(OrderTerms(q.xr)?.showRaw())
      OrderTerms(q.xr) shouldBe n.xr
    }
  }


//  "sortBy" - {
//    "a.sortBy(b => c).filter(d => e)" in {
//      val q = quote {
//        qr1.sortBy(b => b.s).filter(d => d.s == "s1")
//      }
//      val n = quote {
//        qr1.filter(d => d.s == "s1").sortBy(b => b.s)
//      }
//      OrderTerms.unapply(q.ast) mustEqual Some(n.ast)
//    }
//  }

  "a.flatMap(b => c).?.map(d => e)" - {
    "take" {
      val q = qr1.flatMap { b -> qr2 }.take(3).map { d -> d.s }
      val n = qr1.flatMap { b -> qr2 }.map { d -> d.s }.take(3)
      OrderTerms(q.xr) shouldBe n.xr
    }

//    "take" in {
//      val q = quote {
//        qr1.flatMap(b => qr2).take(3).map(d => d.s)
//      }
//      val n = quote {
//        qr1.flatMap(b => qr2).map(d => d.s).take(3)
//      }
//      OrderTerms.unapply(q.ast) mustEqual Some(n.ast)
//    }

    "drop" {
      val q = qr1.flatMap { b -> qr2 }.drop(3).map { d -> d.s }
      val n = qr1.flatMap { b -> qr2 }.map { d -> d.s }.drop(3)
      OrderTerms(q.xr) shouldBe n.xr
    }

//    "drop" in {
//      val q = quote {
//        qr1.flatMap(b => qr2).drop(3).map(d => d.s)
//      }
//      val n = quote {
//        qr1.flatMap(b => qr2).map(d => d.s).drop(3)
//      }
//      OrderTerms.unapply(q.ast) mustEqual Some(n.ast)
//    }
//  }
  }
})