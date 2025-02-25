@file:TracesEnabled(TraceType.SqlQueryConstruct::class, TraceType.SqlNormalizations::class, TraceType.Normalizations::class)
@file:io.exoquery.annotation.ExoGoldenTest

package io.exoquery

import io.exoquery.annotation.TracesEnabled
import io.exoquery.testdata.Address
import io.exoquery.testdata.Person
import io.exoquery.testdata.Robot
import io.exoquery.util.TraceType
import io.kotest.core.spec.style.FreeSpec

// Note that the 1st time you overwrite the golden file it will still fail because the compile is using teh old version
// Also note that it won't actually override the BasicQuerySanitySpecGolden file unless you change this one

// build the file BasicQuerySanitySpecGolden.kt, is that as the constructor arg
class QueryAdvancedReq : GoldenSpec(QueryAdvancedReqGolden, {

  // TODO implement and test deconstruction e.g. val (p, a) = from(Table<PersonToAddress>())
  "select clause + join + nested filters" {
    val people =
      select {
        val p = from(Table<Person>().filter { p -> p.age > 18 })
        val a = join(Table<Address>().filter { a -> a.street == "123 St." }){ a -> a.ownerId == p.id }
        p to a
      }
    people.buildPretty<PostgresDialect>("select clause + join + nested filters").shouldBeGolden()
  }
  "select clauses from(nested)" {
    val people =
      select {
        val pa = from(
          select {
            val p = from(Table<Person>())
            val a = join(Table<Address>()){ a -> a.ownerId == p.id }
            p to a
          }
        )
        val a = join(Table<Robot>()) { r -> r.ownerId == pa.first.id }
        pa to a
      }
    people.buildPretty<PostgresDialect>("select clauses from(nested)").shouldBeGolden()
  }
  "select clauses join(nested)" {

  }
  "capture + select-clause + filters" {

  }
  "multiple from-clauses" {

  }


})
