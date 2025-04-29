package io.exoquery

import io.exoquery.sql.MySqlDialect
import io.exoquery.sql.PostgresDialect
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

// Note that the 1st time you overwrite the golden file it will still fail because the compile is using teh old version
// Also note that it won't actually override the BasicQuerySanitySpecGolden file unless you change this one

// build the file BasicQuerySanitySpecGolden.kt, is that as the constructor arg
class QueryReq : GoldenSpecDynamic(QueryReqGoldenDynamic, Mode.ExoGoldenTest(), {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val city: String)

  "basic query" {
    val people = capture { Table<Person>() }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with map" {
    val people = capture { Table<Person>().map { p -> p.name } }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "map with aggregation" {
    val people = capture { Table<Person>().map { p -> avg(p.age) } }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with filter" {
    val people = capture { Table<Person>().filter { p -> p.age > 18 } }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with where" {
    val people = capture { Table<Person>().where { age > 18 } }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }

  "filter + correlated isEmpty" {
    val people = capture { Table<Person>().filter { p -> p.age > Table<Person>().map { p -> p.age }.avg() } }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }

  "filter + correlated + value" {
    val people = capture { Table<Person>().filter { p -> p.age > Table<Person>().map { p -> avg(p.age) - min(p.age) }.value() } }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }

  "query with flatMap" {
    val people = capture { Table<Person>().flatMap { p -> Table<Address>().filter { a -> a.ownerId == p.id } } }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with union" {
    val people = capture { (Table<Person>().filter { p -> p.age > 18 } union Table<Person>().filter { p -> p.age < 18 }) }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with unionAll" {
    val people = capture { (Table<Person>().filter { p -> p.age > 18 } unionAll Table<Person>().filter { p -> p.age < 18 }) }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with unionAll - symbolic" {
    val people = capture { (Table<Person>().filter { p -> p.age > 18 } + Table<Person>().filter { p -> p.age < 18 }) }
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with surrounding free" {
    val q = capture {
      free("${Table<Person>().filter { p -> p.name == "Joe" }} FOR UPDATE").asPure<SqlQuery<Person>>()
    }
    shouldBeGolden(q.xr, "XR")
    shouldBeGolden(q.build<PostgresDialect>())
  }

})
