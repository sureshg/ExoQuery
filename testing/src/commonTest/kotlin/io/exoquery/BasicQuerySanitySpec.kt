package io.exoquery

import io.kotest.core.spec.style.FreeSpec

// Note that the 1st time you overwrite the golden file it will still fail because the compile is using teh old version
// Also note that it won't actually override the BasicQuerySanitySpecGolden file unless you change this one

// build the file BasicQuerySanitySpecGolden.kt, is that as the constructor arg
class BasicQuerySanitySpec : GoldenSpecDynamic(BasicQuerySanitySpecGoldenDynamic, Mode.ExoGoldenTest(), {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val city: String)

  "basic query" {
    val people = capture { Table<Person>() }
    people.build<PostgresDialect>("basic query").shouldBeGolden()
    // people.buildPretty(PostgresDialect::class)

  }
  "query with map" {
    val people = capture { Table<Person>().map { p -> p.name } }
    shouldBeGolden(people.build<PostgresDialect>())
  }
  "query with filter" {
    val people = capture { Table<Person>().filter { p -> p.age > 18 } }
    shouldBeGolden(people.build<PostgresDialect>())
  }

})
