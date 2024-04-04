package io.exoquery.norm

import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.exoquery.util.TraceConfig
import io.exoquery.util.andThen
import io.exoquery.xr.StatelessTransformer
import io.kotest.matchers.shouldBe
import io.exoquery.xr.XR
import io.exoquery.xr.XR.*
import io.exoquery.xr.csf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual

class AttachToEntitySpec: FreeSpec({
  val replaceTempIdent: StatelessTransformer = object: StatelessTransformer {
    override fun invokeIdent(xr: Ident): Ident =
      when {
        xr.isTemporary() -> XR.Ident.csf("x")(xr)
        else -> xr
      }
  }

  val attachToEntity = { x: XR.Query ->
    AttachToEntity({ a: XR.Query, b: XR.Ident -> SortBy(a, b, XR.Const.Int(1), Ordering.Asc) }).invoke(x)
      .let {  replaceTempIdent(it) }
  }


  "attaches clause to the root of the query (entity)" - {
    "query is the entity" {
      val n = qr1.sortedBy { x -> 1 }
      attachToEntity(qr1.xr) shouldBeEqual n.xr
    }

//    "query is the entity" in {
//      val n = quote {
//        qr1.sortBy(x => 1)
//      }
//      attachToEntity(qr1.ast) mustEqual n.ast
//    }

    "query is a composition" - {
      "map" {
        val q = qr1.filter { t -> t.i == 1 }.map { t -> t.s }
        val n = qr1.sortedBy { t -> 1 }.filter { t -> t.i == 1 }.map { t -> t.s }
        attachToEntity(q.xr) shouldBeEqual n.xr
      }

//    "query is a composition" - {
//      "map" in {
//        val q = quote {
//          qr1.filter(t => t.i == 1).map(t => t.s)
//        }
//        val n = quote {
//          qr1.sortBy(t => 1).filter(t => t.i == 1).map(t => t.s)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

      "flatMap" {
        val q = qr1.filter { t -> t.i == 1 }.flatMap { t -> qr2 }
        val n = qr1.sortedBy { t -> 1 }.filter { t -> t.i == 1 }.flatMap { t -> qr2 }
        attachToEntity(q.xr) shouldBe n.xr
      }

//      "flatMap" in {
//        val q = quote {
//          qr1.filter(t => t.i == 1).flatMap(t => qr2)
//        }
//        val n = quote {
//          qr1.sortBy(t => 1).filter(t => t.i == 1).flatMap(t => qr2)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

      "concatMap" {
        val q = qr1.filter { t -> t.i == 1 }.concatMap { t -> t.s.split(" ") }
        println("=================Value: " + q.xr.showRaw(true))

        //val n = qr1.sortedBy { 1 }.filter { t -> t.i == 1 }.concatMap { t -> t.s.split(" ") }
        //attachToEntity(q.xr) shouldBe n.xr
      }

//      "concatMap" in {
//        val q = quote {
//          qr1.filter(t => t.i == 1).concatMap(t => t.s.split(" "))
//        }
//        val n = quote {
//          qr1.sortBy(t => 1).filter(t => t.i == 1).concatMap(t => t.s.split(" "))
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

      "filter" {
        val q = qr1.filter { t -> t.i == 1 }.filter { t -> t.s == "s1" }
        val n = qr1.sortedBy { t -> 1 }.filter { t -> t.i == 1 }.filter { t -> t.s == "s1" }
        attachToEntity(q.xr) shouldBe n.xr
      }

//      "filter" in {
//        val q = quote {
//          qr1.filter(t => t.i == 1).filter(t => t.s == "s1")
//        }
//        val n = quote {
//          qr1.sortBy(t => 1).filter(t => t.i == 1).filter(t => t.s == "s1")
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

      "sortedBy" {
        val q = qr1.sortedBy { t -> t.s }
        val n = qr1.sortedBy { t -> 1 }.sortedBy { t -> t.s }
        attachToEntity(q.xr) shouldBe n.xr
      }

//      "sortBy" in {
//        val q = quote {
//          qr1.sortBy(t => t.s)
//        }
//        val n = quote {
//          qr1.sortBy(t => 1).sortBy(t => t.s)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

      "take" {
        val q = qr1.sortedBy { it.s }.take(1)
        val n = qr1.sortedBy { 1 }.sortedBy { it.s }.take(1)
        attachToEntity(q.xr) shouldBe n.xr
      }

//      "take" in {
//        val q = quote {
//          qr1.sortBy(b => b.s).take(1)
//        }
//        val n = quote {
//          qr1.sortBy(b => 1).sortBy(b => b.s).take(1)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

      "drop" {
        val q = qr1.sortedBy { it.s }.drop(1)
        val n = qr1.sortedBy { 1 }.sortedBy { it.s }.drop(1)
        attachToEntity(q.xr) shouldBe n.xr
      }

//      "drop" in {
//        val q = quote {
//          qr1.sortBy(b => b.s).drop(1)
//        }
//        val n = quote {
//          qr1.sortBy(b => 1).sortBy(b => b.s).drop(1)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

      "distinct" {
        val q = qr1.sortedBy { it.s }.drop(1).distinct()
        val n = qr1.sortedBy { 1 }.sortedBy { it.s }.drop(1).distinct()
        attachToEntity(q.xr) shouldBe n.xr
      }

//      "distinct" in {
//        val q = quote {
//          qr1.sortBy(b => b.s).drop(1).distinct
//        }
//        val n = quote {
//          qr1.sortBy(b => 1).sortBy(b => b.s).drop(1).distinct
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }
    }
  }

  val iqr1 = SQL<TestEntity>("$qr1").asQuery()

// Scala:
//  val iqr1 = quote {
//    sql"$qr1".as[Query[TestEntity]]
//  }

  "query is the entity" {
    val n = iqr1.sortedBy { x -> 1 }
    attachToEntity(iqr1.xr) shouldBeEqual n.xr
  }

// Scala:
//  "query is the entity" in {
//    val n = quote {
//      iqr1.sortBy(x => 1)
//    }
//    attachToEntity(iqr1.ast) mustEqual n.ast
//  }

// Kotlin:
//  "attaches clause to the root of the query (infix)" - {
//    "query is the entity" in {
//      val n = quote {
//        iqr1.sortBy(x => 1)
//      }
//      attachToEntity(iqr1.ast) mustEqual n.ast
//    }

  "query is a composition" {
    val q = iqr1.filter { t -> t.i == 1 }.map { t -> t.s }
    val n = iqr1.sortedBy { t -> 1 }.filter { t -> t.i == 1 }.map { t -> t.s }
    attachToEntity(q.xr) shouldBeEqual n.xr
  }

//    "query is a composition" - {
//      "map" in {
//        val q = quote {
//          iqr1.filter(t => t.i == 1).map(t => t.s)
//        }
//        val n = quote {
//          iqr1.sortBy(t => 1).filter(t => t.i == 1).map(t => t.s)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

  "flatMap" {
    val q = iqr1.filter { t -> t.i == 1 }.flatMap { t -> qr2 }
    val n = iqr1.sortedBy { t -> 1 }.filter { t -> t.i == 1 }.flatMap { t -> qr2 }
    attachToEntity(q.xr) shouldBeEqual n.xr
  }

//      "flatMap" in {
//        val q = quote {
//          iqr1.filter(t => t.i == 1).flatMap(t => qr2)
//        }
//        val n = quote {
//          iqr1.sortBy(t => 1).filter(t => t.i == 1).flatMap(t => qr2)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

  "concatMap" {
    val q = iqr1.filter { t -> t.i == 1 }.concatMap { t -> t.s.split(" ") }
    val n = iqr1.sortedBy { t -> 1 }.filter { t -> t.i == 1 }.concatMap { t -> t.s.split(" ") }
    attachToEntity(q.xr) shouldBeEqual n.xr
  }

//      "concatMap" in {
//        val q = quote {
//          iqr1.filter(t => t.i == 1).concatMap(t => t.s.split(" "))
//        }
//        val n = quote {
//          iqr1.sortBy(t => 1).filter(t => t.i == 1).concatMap(t => t.s.split(" "))
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

  "filter" {
    val q = iqr1.filter { t -> t.i == 1 }.filter { t -> t.s == "s1" }
    val n = iqr1.sortedBy { t -> 1 }.filter { t -> t.i == 1 }.filter { t -> t.s == "s1" }
    attachToEntity(q.xr) shouldBeEqual n.xr
  }

//      "filter" in {
//        val q = quote {
//          iqr1.filter(t => t.i == 1).filter(t => t.s == "s1")
//        }
//        val n = quote {
//          iqr1.sortBy(t => 1).filter(t => t.i == 1).filter(t => t.s == "s1")
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

  "sortedBy" {
    val q = iqr1.sortedBy { t -> t.s }
    val n = iqr1.sortedBy { t -> 1 }.sortedBy { t -> t.s }
    attachToEntity(q.xr) shouldBeEqual n.xr
  }

//      "sortBy" in {
//        val q = quote {
//          iqr1.sortBy(t => t.s)
//        }
//        val n = quote {
//          iqr1.sortBy(t => 1).sortBy(t => t.s)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

  "take" {
    val q = iqr1.sortedBy { b -> b.s }.take(1)
    val n = iqr1.sortedBy { b -> 1 }.sortedBy { b -> b.s }.take(1)
    attachToEntity(q.xr) shouldBeEqual n.xr

  }

//      "take" in {
//        val q = quote {
//          iqr1.sortBy(b => b.s).take(1)
//        }
//        val n = quote {
//          iqr1.sortBy(b => 1).sortBy(b => b.s).take(1)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

  "drop" {
    val q = iqr1.sortedBy { b -> b.s }.drop(1)
    val n = iqr1.sortedBy { b -> 1 }.sortedBy { b -> b.s }.drop(1)
    attachToEntity(q.xr) shouldBeEqual n.xr
  }

//      "drop" in {
//        val q = quote {
//          iqr1.sortBy(b => b.s).drop(1)
//        }
//        val n = quote {
//          iqr1.sortBy(b => 1).sortBy(b => b.s).drop(1)
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }

  "distinct" {
    val q = iqr1.sortedBy { b -> b.s }.drop(1).distinct()
    val n = iqr1.sortedBy { 1 }.sortedBy { b -> b.s }.drop(1).distinct()
    attachToEntity(q.xr) shouldBeEqual n.xr
  }

//      "distinct" in {
//        val q = quote {
//          iqr1.sortBy(b => b.s).drop(1).distinct
//        }
//        val n = quote {
//          iqr1.sortBy(b => 1).sortBy(b => b.s).drop(1).distinct
//        }
//        attachToEntity(q.ast) mustEqual n.ast
//      }
//    }
//  }

  "falls back to the query if it's not possible to flatten it" - {
    "union" {
      val q = qr1.union(qr2)
      val n = qr1.union(qr2).sortedBy { x -> 1 }
      attachToEntity(q.xr) shouldBeEqual n.xr
    }

//    "union" in {
//      val q = quote {
//        qr1.union(qr2)
//      }
//      val n = quote {
//        qr1.union(qr2).sortBy(x => 1)
//      }
//      attachToEntity(q.ast) mustEqual n.ast
//    }

    "unionAll" {
      val q = qr1.unionAll(qr2)
      val n = qr1.unionAll(qr2).sortedBy { x -> 1 }
      attachToEntity(q.xr) shouldBeEqual n.xr
    }

//    "unionAll" in {
//      val q = quote {
//        qr1.unionAll(qr2)
//      }
//      val n = quote {
//        qr1.unionAll(qr2).sortBy(x => 1)
//      }
//      attachToEntity(q.ast) mustEqual n.ast
//    }

    "groupBy.map" {
      val q = qr1.groupBy { a -> a.i }.map { a -> 1 }
      val n = qr1.groupBy { a -> a.i }.map { a -> 1 }.sortedBy { x -> 1 }
      attachToEntity(q.xr) shouldBeEqual n.xr
    }

//    "groupBy.map" in {
//      val q = quote {
//        qr1.groupBy(a => a.i).map(a => 1)
//      }
//      val n = quote {
//        qr1.groupBy(a => a.i).map(a => 1).sortBy(x => 1)
//      }
//      attachToEntity(q.ast) mustEqual n.ast
//    }
//  }

    "fails if the entity isn't found" {
      shouldThrow<TransformXrError> {
        attachToEntity(Map(Marker("a", XR.Const.Null()), Ident("b"), Ident("c")))
      }
    }

//  "fails if the entity isn't found" in {
//    intercept[IllegalStateException] {
//      attachToEntity(Map(Ident("a"), Ident("b"), Ident("c")))
//    }
//    ()
//  }

  }
})