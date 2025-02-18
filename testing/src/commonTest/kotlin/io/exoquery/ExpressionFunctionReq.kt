@file:io.exoquery.annotation.TracesEnabled(TraceType.Normalizations::class, TraceType.SqlNormalizations::class)
package io.exoquery

import io.exoquery.annotation.CapturedFunction
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.testdata.*
import io.exoquery.util.TraceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain

class ExpressionFunctionReq : GoldenSpecDynamic(ExpressionFunctionReqGoldenDynamic, Mode.ExoGoldenTest(), {
  "String" - {
    "toInt" {
      val q = capture { Table<Person>().map { p -> p.name.toInt() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toLong" {
      val q = capture { Table<Person>().map { p -> p.name.toLong() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toBoolean" {
      val q = capture { Table<Person>().map { p -> p.name.toBoolean() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
  }
  "Int" - {
    "toLong" {
      val q = capture { Table<Person>().map { p -> p.age.toLong() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toDouble" {
      val q = capture { Table<Person>().map { p -> p.age.toDouble() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toFloat" {
      val q = capture { Table<Person>().map { p -> p.age.toFloat() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
  }
  "Long" - {
    "toInt" {
      val q = capture { Table<Person>().map { p -> p.age.toInt() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toDouble" {
      val q = capture { Table<Person>().map { p -> p.age.toDouble() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toFloat" {
      val q = capture { Table<Person>().map { p -> p.age.toFloat() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
  }
  "Float" - {
    "toInt" {
      val q = capture { Table<Person>().map { p -> p.age.toInt() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toLong" {
      val q = capture { Table<Person>().map { p -> p.age.toLong() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
    "toDouble" {
      val q = capture { Table<Person>().map { p -> p.age.toDouble() } }
      shouldBeGolden(q.build<PostgresDialect>())
    }
  }
})
