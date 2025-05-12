package io.exoquery.sqlite

import io.exoquery.SqlQuery
import io.exoquery.TestDatabases
import io.exoquery.capture
import io.exoquery.runOn
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CastingSpec : FreeSpec({
  val ctx = TestDatabases.sqlite

  "Int to String" {
    val q = capture { free("SELECT 1 AS value").asPure<SqlQuery<Int>>().map { it.toString() } }
    q.buildFor.Sqlite().runOn(ctx).first() shouldBe "1"
  }

  "String to Long" {
    val q = capture { free("SELECT '1' AS value").asPure<SqlQuery<String>>().map { it.toLong() } }
    q.buildFor.Sqlite().runOn(ctx).first() shouldBe 1
  }
  "String to Int" {
    val q = capture { free("SELECT '1' AS value").asPure<SqlQuery<String>>().map { it.toInt() } }
    q.buildFor.Sqlite().runOn(ctx).first() shouldBe 1
  }
  "String to Short" {
    val q = capture { free("SELECT '1' AS value").asPure<SqlQuery<String>>().map { it.toShort() } }
    q.buildFor.Sqlite().runOn(ctx).first() shouldBe 1
  }
  "String to Double" {
    val q = capture { free("SELECT '1.2' AS value").asPure<SqlQuery<String>>().map { it.toDouble() } }
    q.buildFor.Sqlite().runOn(ctx).first() shouldBe (1.2).toDouble()
  }
  "String to Float" {
    val q = capture { free("SELECT '1.2' AS value").asPure<SqlQuery<String>>().map { it.toFloat() } }
    q.buildFor.Sqlite().runOn(ctx).first() shouldBe (1.2).toFloat()
  }
  "String to Boolean" {
    val q = capture { free("SELECT 'true' AS value").asPure<SqlQuery<String>>().map { it.toBoolean() } }
    q.buildFor.Sqlite().runOn(ctx).first() shouldBe true
  }
})
