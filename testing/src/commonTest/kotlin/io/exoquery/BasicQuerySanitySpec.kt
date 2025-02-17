@file:io.exoquery.annotation.ExoGoldenOverride
package io.exoquery

import io.kotest.core.spec.style.FreeSpec

// Note that the 1st time you overwrite the golden file it will still fail because the compile is using teh old version
// Also note that it won't actually override the BasicQuerySanitySpecGolden file unless you change this one

// build the file BasicQuerySanitySpecGolden.kt, is that as the constructor arg
class BasicQuerySanitySpec : GoldenSpec(BasicQuerySanitySpecGolden, {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val city: String)

  "basic query" {
    val people = capture { Table<Person>() }
    people.buildPretty<PostgresDialect>("basic query").shouldBeGolden()
  }
  "query with map" {
    val people = capture { Table<Person>().map { p -> p.name } }
    people.buildPretty<PostgresDialect>("query with map").shouldBeGolden()
  }
  "query with filter" {
    val people = capture { Table<Person>().filter { p -> p.age > 18 } }
    people.buildPretty<PostgresDialect>("query with filter").shouldBeGolden()
  }
  //"query with co-releated in filter" {
  //  val people = capture { Table<Person>().filter { p -> Table<Address>().filter { a -> a.ownerId == p.id }.isNotEmpty() } }
  //  people.build<PostgresDialect>("query with co-releated in filter").shouldBeGolden()
  //}
  //val x = listOf(1,2,3)
  //x.isNotEmpty()
})
