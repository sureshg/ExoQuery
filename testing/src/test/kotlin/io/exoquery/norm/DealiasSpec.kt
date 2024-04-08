package io.exoquery.norm

import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.exoquery.util.TraceConfig
import io.kotest.matchers.shouldBe

class DealiasSpec: FreeSpec({

  val Dealias = DealiasApply(TraceConfig.empty)

  "ensures that each entity is referenced by the same alias" - {
    "flatMap" {
      val q = qr1.filter { a -> a.s == "s" }.flatMap { b -> qr2 }
      val n = qr1.filter { a -> a.s == "s" }.flatMap { a -> qr2 }
      Dealias(q.xr) shouldBe n.xr
    }

//    "flatMap" in {
//      val q = quote {
//        qr1.filter(a => a.s == "s").flatMap(b => qr2)
//      }
//      val n = quote {
//        qr1.filter(a => a.s == "s").flatMap(a => qr2)
//      }
//      Dealias(q.ast) mustEqual n.ast
//    }

    "concatMap" {
      val q = qr1.filter { a -> a.s == "s" }.concatMap { b -> b.s.split(" ") }
      val n = qr1.filter { a -> a.s == "s" }.concatMap { a -> a.s.split(" ") }
      Dealias(q.xr) shouldBe n.xr
    }

//    "concatMap" in {
//      val q = quote {
//        qr1.filter(a => a.s == "s").concatMap(b => b.s.split(" "))
//      }
//      val n = quote {
//        qr1.filter(a => a.s == "s").concatMap(a => a.s.split(" "))
//      }
//      Dealias(q.ast) mustEqual n.ast
//    }

    "map" {
      val q = qr1.filter { a -> a.s == "s" }.map { b -> b.s }
      val n = qr1.filter { a -> a.s == "s" }.map { a -> a.s }
      Dealias(q.xr) shouldBe n.xr
    }

//    "map" in {
//      val q = quote {
//        qr1.filter(a => a.s == "s").map(b => b.s)
//      }
//      val n = quote {
//        qr1.filter(a => a.s == "s").map(a => a.s)
//      }
//      Dealias(q.ast) mustEqual n.ast
//    }

    "filter" {
      val q = qr1.filter { a -> a.s == "s" }.filter { b -> b.s != "l" }
      val n = qr1.filter { a -> a.s == "s" }.filter { a -> a.s != "l" }
      Dealias(q.xr) shouldBe n.xr
    }

//    "filter" in {
//      val q = quote {
//        qr1.filter(a => a.s == "s").filter(b => b.s != "l")
//      }
//      val n = quote {
//        qr1.filter(a => a.s == "s").filter(a => a.s != "l")
//      }
//      Dealias(q.ast) mustEqual n.ast
//    }

    "sortBy" {
      val q = qr1.filter { a -> a.s == "s" }.sortedBy { b -> b.s }
      val n = qr1.filter { a -> a.s == "s" }.sortedBy { a -> a.s }
      Dealias(q.xr) shouldBe n.xr
    }

//    "sortBy" in {
//      val q = quote {
//        qr1.filter(a => a.s == "s").sortBy(b => b.s)
//      }
//      val n = quote {
//        qr1.filter(a => a.s == "s").sortBy(a => a.s)
//      }
//      Dealias(q.ast) mustEqual n.ast
//    }

    // TODO re-enable when ExoQuery supports these operators
    //"aggregation" {
    //  val q = qr1.map { a -> a.i }.max()
    //  Dealias(q.xr) shouldBe q.xr
    //}

//    "aggregation" in {
//      val q = quote {
//        qr1.map(a => a.i).max
//      }
//      Dealias(q.ast) mustEqual q.ast
//    }

    "take" {
      val q = qr1.filter { a -> a.s == "s" }.take(10).map { b -> b.s }
      val n = qr1.filter { a -> a.s == "s" }.take(10).map { a -> a.s }
      Dealias(q.xr) shouldBe n.xr
    }

//    "take" in {
//      val q = quote {
//        qr1.filter(a => a.s == "s").take(10).map(b => b.s)
//      }
//      val n = quote {
//        qr1.filter(a => a.s == "s").take(10).map(a => a.s)
//      }
//      Dealias(q.ast) mustEqual n.ast
//    }

    "drop" {
      val q = qr1.filter { a -> a.s == "s" }.drop(10).map { b -> b.s }
      val n = qr1.filter { a -> a.s == "s" }.drop(10).map { a -> a.s }
      Dealias(q.xr) shouldBe n.xr
    }

//    "drop" in {
//      val q = quote {
//        qr1.filter(a => a.s == "s").drop(10).map(b => b.s)
//      }
//      val n = quote {
//        qr1.filter(a => a.s == "s").drop(10).map(a => a.s)
//      }
//      Dealias(q.ast) mustEqual n.ast
//    }

    "union" - {
      "left" {
        val q = qr1.filter { a -> a.s == "s" }.map { b -> b.s }.union(qr1)
        val n = qr1.filter { a -> a.s == "s" }.map { a -> a.s }.union(qr1)
        Dealias(q.xr) shouldBe n.xr
      }

//      "left" in {
//        val q = quote {
//          qr1.filter(a => a.s == "s").map(b => b.s).union(qr1)
//        }
//        val n = quote {
//          qr1.filter(a => a.s == "s").map(a => a.s).union(qr1)
//        }
//        Dealias(q.ast) mustEqual n.ast
//      }

      "right" {
        val q = qr1.union(qr1.filter { a -> a.s == "s" }.map { b -> b.s })
        val n = qr1.union(qr1.filter { a -> a.s == "s" }.map { a -> a.s })
        Dealias(q.xr) shouldBe n.xr
      }
    }

//      "right" in {
//        val q = quote {
//          qr1.union(qr1.filter(a => a.s == "s").map(b => b.s))
//        }
//        val n = quote {
//          qr1.union(qr1.filter(a => a.s == "s").map(a => a.s))
//        }
//        Dealias(q.ast) mustEqual n.ast
//      }
//    }



    "unionAll" - {
      "left" {
        val q = qr1.filter { a -> a.s == "s" }.map { b -> b.s }.unionAll(qr1)
        val n = qr1.filter { a -> a.s == "s" }.map { a -> a.s }.unionAll(qr1)
        Dealias(q.xr) shouldBe n.xr
      }

//      "left" in {
//        val q = quote {
//          qr1.filter(a => a.s == "s").map(b => b.s).unionAll(qr1)
//        }
//        val n = quote {
//          qr1.filter(a => a.s == "s").map(a => a.s).unionAll(qr1)
//        }
//        Dealias(q.ast) mustEqual n.ast
//      }

      "right" {
        val q = qr1.unionAll(qr1.filter { a -> a.s == "s" }.map { b -> b.s })
        val n = qr1.unionAll(qr1.filter { a -> a.s == "s" }.map { a -> a.s })
        Dealias(q.xr) shouldBe n.xr
      }

//      "right" in {
//        val q = quote {
//          qr1.unionAll(qr1.filter(a => a.s == "s").map(b => b.s))
//        }
//        val n = quote {
//          qr1.unionAll(qr1.filter(a => a.s == "s").map(a => a.s))
//        }
//        Dealias(q.ast) mustEqual n.ast
//      }
    }

    "entity" {
      Dealias(qr1.xr) shouldBe qr1.xr
    }
//    "entity" in {
//      Dealias(qr1.ast) mustEqual qr1.ast
//    }

    "distinct" {
      val q = qr1.map { a -> a.i }.distinct()
      Dealias(q.xr) shouldBe q.xr
    }

//    "distinct" in {
//      val q = quote {
//        qr1.map(a => a.i).distinct
//      }
//      Dealias(q.ast) mustEqual q.ast
//    }
  }



})