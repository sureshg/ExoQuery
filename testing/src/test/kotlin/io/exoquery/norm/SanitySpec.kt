package io.exoquery.norm

import io.exoquery.*
import io.exoquery.select
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SanitySpec: FreeSpec({
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
    "flatJoin" {
      val q =
        query {
          val a = from(qr1)
          val b = join(qr2).on { s == a().s }
          select { a() to b() }
        }
      q.xr.show() shouldBe """query("TestEntity").flatMap { a -> query("TestEntity2").join { b1 -> b1.s == a.s }.map { b -> Tuple2(_1: a, _2: b) } }"""
    }
    "flatJoin + where + groupBy + sortBy" {
      val q =
        query {
          val a = from(qr1)
          val b = join(qr2).on { s == a().s }
          where { a().i == 123 }
          groupBy { b().s }
          sortedBy { a().l }
          select { a() to b() }
        }
      q.xr.show() shouldBe """query("TestEntity").flatMap { a -> query("TestEntity2").join { b1 -> b1.s == a.s }.flatMap { b -> flatFilter { a.i == 123 }.flatMap { unused -> flatGroupBy { b.s }.flatMap { unused -> flatSortBy { a.l }(Ordering.ASC).map { unused -> Tuple2(_1: a, _2: b) } } } } }"""
    }
  }

  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val id: Int, val owner: Int, val street: String)
})

fun main() {

}
