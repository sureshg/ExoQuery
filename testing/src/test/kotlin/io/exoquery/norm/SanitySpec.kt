package io.exoquery.norm

import io.exoquery.*
import io.exoquery.printing.exoPrint
import io.exoquery.select
import io.exoquery.sql.token
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SanitySpec: FreeSpec({
  val dol = '$'

  val Dialect = PostgresDialect(TraceConfig.empty)

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
      val q = qr1.groupBy { x -> x.i }.map { i -> i }
      q.xr.show() shouldBe """query("TestEntity").groupByMap { x -> x.i } { i -> i }"""
      Dialect.show(q.xr) shouldBe "SELECT x.s, x.i, x.l, x.o, x.b FROM TestEntity x GROUP BY x.i"
    }
    "flatMap" {
      val q = qr1.flatMap { x -> qr2.filter { y -> y.s == x.s } }
      q.xr.show() shouldBe """query("TestEntity").flatMap { x -> query("TestEntity2").filter { y -> y.s == x.s } }"""
      Dialect.show(q.xr) shouldBe "SELECT y.s, y.i, y.l, y.o, y.b FROM TestEntity x, TestEntity2 y WHERE y.s = x.s"
    }
    "infix" - {
      val q = SQL<TestEntity>("foobar").asQuery().map { t -> t.i }
      q.xr.show() shouldBe """SQL("foobar").map { t -> t.i }"""
      Dialect.show(q.xr) shouldBe "SELECT t.i FROM (foobar) AS t"
    }
    "distinct" {
      val q = qr1.map { x -> x.s }.distinct()
      q.xr.show() shouldBe """query("TestEntity").map { x -> x.s }.distinct"""
      Dialect.show(q.xr) shouldBe "SELECT DISTINCT x.s FROM TestEntity x"
    }
    "distinct - row" {
      val q = qr1.distinct()
      q.xr.show() shouldBe """query("TestEntity").distinct"""
      Dialect.show(q.xr) shouldBe """SELECT DISTINCT x.s, x.i, x.l, x.o, x.b FROM TestEntity x"""
    }
    "flatJoin" {
      val q =
        query {
          val a = from(qr1)
          val b = join(qr2).on { s == a().s }
          select { a() to b() }
        }
      q.xr.show() shouldBe """query("TestEntity").flatMap { a -> query("TestEntity2").join { b -> b.s == a.s }.map { b -> Tuple(first: a, second: b) } }"""
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
      q.xr.show() shouldBe """query("TestEntity").flatMap { a -> query("TestEntity2").join { b -> b.s == a.s }.flatMap { b -> flatFilter { a.i == 123 }.flatMap { unused -> flatGroupBy { b.s }.flatMap { unused -> flatSortBy { a.l }(Ordering.Asc).map { unused -> Tuple(first: a, second: b) } } } } }"""
      Dialect.show(q.xr) shouldBe "SELECT a.s, a.i, a.l, a.o, a.b, b.s, b.i, b.l, b.o, b.b FROM TestEntity a LEFT JOIN TestEntity2 b ON b.s = a.s WHERE a.i = 123 GROUP BY b.s ORDER BY a.l ASC"
    }
    "size" {
      val q = qr1.size()
      q.xr.show() shouldBe """query("TestEntity").map { x -> x.size }"""
      Dialect.show(q.xr) shouldBe "SELECT COUNT(*) FROM TestEntity x"
    }
    "size - splice" {
      val q = qr2.filter { t -> t.i == qr1.size().value() }
      q.xr.show() shouldBe """query("TestEntity2").filter { t -> t.i == query("TestEntity").map { x -> x.size }.value }"""
      Dialect.show(q.xr) shouldBe "SELECT t.s, t.i, t.l, t.o, t.b FROM TestEntity2 t WHERE t.i = SELECT COUNT(*) FROM TestEntity x"
    }
  }

  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val id: Int, val owner: Int, val street: String)
})
