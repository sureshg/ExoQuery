package io.exoquery.norm

import io.exoquery.*
import io.exoquery.select
import io.exoquery.select.on
import io.exoquery.util.TraceConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class InfixSpec: FreeSpec({
  val dol = '$'

  val Dialect = PostgresDialect(TraceConfig.empty)

  "infix" - {
    "query" - {
      "simple" {
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
          val i = join(SQL<TestEntity2> { "foobar(${t.s})" }.asQuery()).on { i == t.i }
          select { t to i }
        }
        q.xr.show() shouldBe """query("TestEntity").flatMap { t -> SQL("foobar(${dol}{t.s})").join { i -> i.i == t.i }.map { i -> Tuple(first: t, second: i) } }"""
      }
      "composite - expression + query inside" {
        val q = Table<TestEntity2>().flatMap { t2 -> SQL<TestEntity>("foobar(${Table<TestEntity>().filter { tt -> tt.s == "inner" }})").asQuery().map { t -> t.i } }
        q.xr.show() shouldBe """query("TestEntity2").flatMap { t2 -> SQL("foobar(${dol}{query("TestEntity").filter { tt -> tt.s == "inner" }})").map { t -> t.i } }"""
      }
    }
    "expression" - {
      "simple" {
        val q = qr1.filter { t -> t.i == SQL<Int>("foobar").value() }
        q.xr.show() shouldBe """query("TestEntity").filter { t -> t.i == SQL("foobar") }"""
      }
      "composite - expression inside" {
        val q = qr1.filter { t -> t.i == SQL<Int>("foobar(${t.i})").value() }
        q.xr.show() shouldBe """query("TestEntity").filter { t -> t.i == SQL("foobar(${dol}{t.i})") }"""
      }
      "composite - query inside" {
        val q = qr1.filter { t -> t.i == SQL<Int>("foobar(${Table<TestEntity>().filter { tt -> tt.s == "inner" }})").value() }
        q.xr.show() shouldBe """query("TestEntity").filter { t -> t.i == SQL("foobar(${dol}{query("TestEntity").filter { tt -> tt.s == "inner" }})") }"""
        Dialect.show(q.xr) shouldBe "SELECT t.s, t.i, t.l, t.o, t.b FROM TestEntity t WHERE t.i = foobar(SELECT tt.s AS s, tt.i AS i, tt.l AS l, tt.o AS o, tt.b AS b FROM TestEntity tt WHERE tt.s = 'inner')"
      }
    }
  }

})