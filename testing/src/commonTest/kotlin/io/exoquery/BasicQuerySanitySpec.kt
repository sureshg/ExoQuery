package io.exoquery

import io.exoquery.xr.`+++`
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual

class BasicQuerySanitySpec : FreeSpec({
  data class Person(val name: String, val age: Int)

  "basic query" {
    val cap0 = capture { Table<Person>() }
    val cap = capture { cap0.filter { p -> p.name == "Joe" }.map { p -> p.name } }
    cap.build(PostgresDialect())
  }
})
