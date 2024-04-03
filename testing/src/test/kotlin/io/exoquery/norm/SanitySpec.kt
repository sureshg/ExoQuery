package io.exoquery.norm

import io.exoquery.*
import io.exoquery.printing.exoPrint
import io.exoquery.select
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SanitySpec: FreeSpec({
  val dol = '$'

  "basic operations" - {
    "map" {
      val q = qr1.map { x -> x.s }
      q.xr.show() shouldBe """query("TestEntity").map { x -> x.s }"""
    }
    "filter" {
      val q = qr1.filter { x -> x.s == "Joe" }
      q.xr.show() shouldBe """query("TestEntity").filter { x -> x.s == "Joe" }"""
    }
    "groupBy/map" {
      val q = qr1.groupBy { x -> x.i }.map { i -> i } // TODO need an aggregation operator DSL function
      println(q.xr.show()) //shouldBe """query("TestEntity").filter { x -> x.s == "Joe" }"""
    }
    "flatMap" {
      val q = qr1.flatMap { x -> qr2.filter { y -> y.s == x.s } }
      q.xr.show() shouldBe """query("TestEntity").flatMap { x -> query("TestEntity2").filter { y -> y.s == x.s } }"""
    }

    "infix" - {
      "query" - {
        "simple" {
          // TODO what happens if the user does = SQL<Query<TestEntity>>("foobar").asValue().map { t -> t }

          val q = SQL<TestEntity>("foobar").asQuery().map { t -> t.i }
          q.xr.show() shouldBe """SQL("foobar").map { t -> t.i }"""
        }
        // "simple - odd" {
        //   // TODO the top-level function that gets returned is 'map'. Since it will blindly take the contents of Query into a new container, we need to write
        //   //      a transformer that either:
        //   //         1. converts the .invoke into a invokeHidden so that an error "No mapping for symbol: VALUE_PARAMETER name:_context_receiver_0 index:0 type:io.exoquery.EnclosedExpression" or
        //   //         2. changes all instances of (_context_receiver_0:io.exoquery.EnclosedExpression) into the function io.exoquery.enclosedExpressionError("put something useful about the context into here")
        //   //            (I think the latter is better)
        //   //      doesn't happen.
        //   val q = Table<TestEntity2>().flatMap { t2 -> SQL<Query<TestEntity>>("foobar(${t2})").invoke().map { t -> t.i } }
        //   q.xr.show() shouldBe """SQL("foobar").map { t -> t.i }"""
        // }
        "composite - query inside" {
          val q = SQL<TestEntity>("foobar(${Table<TestEntity>().filter { tt -> tt.s == "inner" }})").asQuery().map { t -> t.i }
          q.xr.show() shouldBe """SQL("foobar(${dol}{query("TestEntity").filter { tt -> tt.s == "inner" }})").map { t -> t.i }"""
        }
        "composite - expression inside" {
          val q = Table<TestEntity2>().flatMap { bleeblah -> SQL<TestEntity>("foobar(${bleeblah.s})").asQuery().map { t -> t.i } } // bleeblah.i works, must be a constant-detection issue?
          q.xr.show() shouldBe """query("TestEntity2").flatMap { bleeblah -> SQL("foobar(${dol}{bleeblah.s})").map { t -> t.i } }"""
        }
        // TODO composite with lift inside
        "composite - expression inside with context" {
          val q = query {
            val t = from(Table<TestEntity>())
            // I.e. it is impossible to call ${t().s} unside of a "foobar(...)" unless you use the SQL{...} syntax because the SQL(...) cannot have an EnclosedContext
            val i = join(SQL<TestEntity2> { "foobar(${t().s})" }.asQuery()).on { i == t().i }
            select { t to i }
          }
          println(q.xr.show())
        }
        "composite - expression + query inside" {
          val q = Table<TestEntity2>().flatMap { t2 -> SQL<TestEntity>("foobar(${Table<TestEntity>().filter { tt -> tt.s == "inner" }})").asQuery().map { t -> t.i } }
          println(q.xr.show())
        }
      }
      "expression" - {
        "simple" {
          val q = qr1.filter { t -> t.i == SQL<Int>("foobar").asValue() }
          q.xr.show() shouldBe """query("TestEntity").filter { t -> t.i == SQL("foobar") }"""
        }
        "composite - expression inside" {
          val q = qr1.filter { t -> t.i == SQL<Int>("foobar(${t.i})").asValue() }
          q.xr.show() shouldBe """query("TestEntity").filter { t -> t.i == SQL("foobar(${dol}{t.i})") }"""
        }
        "composite - query inside" {
          val q = qr1.filter { t -> t.i == SQL<Int>("foobar(${Table<TestEntity>().filter { tt -> tt.s == "inner" }})").asValue() }
          q.xr.show() shouldBe """query("TestEntity").filter { t -> t.i == SQL("foobar(${dol}{query("TestEntity").filter { tt -> tt.s == "inner" }})") }"""
        }
      }
    }
//
//    // TODO Infixes with stuff inside
//
//    "flatJoin" {
//      val q =
//        query {
//          val a = from(qr1)
//          val b = join(qr2).on { s == a().s }
//          select { a() to b() }
//        }
//      q.xr.show() shouldBe """query("TestEntity").flatMap { a -> query("TestEntity2").join { b1 -> b1.s == a.s }.map { b -> Tuple2(_1: a, _2: b) } }"""
//    }
//    "flatJoin + where + groupBy + sortBy" {
//      val q =
//        query {
//          val a = from(qr1)
//          val b = join(qr2).on { s == a().s }
//          where { a().i == 123 }
//          groupBy { b().s }
//          sortedBy { a().l }
//          select { a() to b() }
//        }
//      q.xr.show() shouldBe """query("TestEntity").flatMap { a -> query("TestEntity2").join { b1 -> b1.s == a.s }.flatMap { b -> flatFilter { a.i == 123 }.flatMap { unused -> flatGroupBy { b.s }.flatMap { unused -> flatSortBy { a.l }(Ordering.ASC).map { unused -> Tuple2(_1: a, _2: b) } } } } }"""
//    }
  }

  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val id: Int, val owner: Int, val street: String)
})

fun main() {

}
