package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object FreeReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "static free/simple sql function" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
    "static free/simple sql function - pure" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
    "static free/simple sql function - condition" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
    "static free/simple sql function - condition" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
  )
}
