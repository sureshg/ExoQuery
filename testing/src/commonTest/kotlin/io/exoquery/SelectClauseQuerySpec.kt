package io.exoquery

import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec

class SelectClauseQuerySpec : FreeSpec({

  "nested datatypes" - {
    data class Name(val first: String, val last: String)
    data class Person(val name: Name?, val age: Int)
    data class Robot(val ownerFirstName: String, val model: String)

    "from + join on nullable" {
      val people =
        select {
          val p = from(Table<Person>())
          val r = join(Table<Robot>()) { r -> p.name?.first == r.ownerFirstName }
          p to r
        }
      println("Query:\n${people.build(PostgresDialect())}")
    }
    "from + join on nullable ?: alternative" {
      val people =
        select {
          val p = from(Table<Person>())
          val r = join(Table<Robot>()) { r -> p.name?.first ?: "defaultName" == r.ownerFirstName }
          p to r
        }
      println("Query:\n${people.build(PostgresDialect())}") //
    }

  }
})