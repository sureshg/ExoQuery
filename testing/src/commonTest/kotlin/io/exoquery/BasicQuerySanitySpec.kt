@file:io.exoquery.annotation.ExoGoldenTest
package io.exoquery

import io.kotest.core.spec.style.FreeSpec

// Note that the 1st time you overwrite the golden file it will still fail because the compile is using teh old version
// Also note that it won't actually override the BasicQuerySanitySpecGolden file unless you change this one

// build the file BasicQuerySanitySpecGolden.kt, is that as the constructor arg
class BasicQuerySanitySpec : GoldenSpec(BasicQuerySanitySpecGolden, { //hello
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val city: String)

  "basic query" {
    val people = capture { Table<Person>() }
    val joes = capture { people.filter { p -> p.name == "Joe" } }
    joes.build(PostgresDialect(), "basic query").shouldBeGolden("basic query")
  }
  "query with join" {
    val query = select {
      val p = from(Table<Person>())
      val a = join(Table<Address>()) { a -> a.ownerId == p.id }
      p to a
    }
    query.buildPretty(PostgresDialect(), "query with join").shouldBeGolden("query with join") //hello
  }
})
