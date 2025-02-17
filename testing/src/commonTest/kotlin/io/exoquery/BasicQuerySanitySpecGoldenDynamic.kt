package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object BasicQuerySanitySpecGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "basic query" to cr(
      "SELECT x.id AS id, x.name AS name, x.age AS age FROM Person x"
    ),
    "query with map" to cr(
      "SELECT p.name AS value FROM Person p"
    ),
    "query with filter" to cr(
      "SELECT p.id AS id, p.name AS name, p.age AS age FROM Person p WHERE p.age > 18"
    ),
  )
}
