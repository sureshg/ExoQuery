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
    "query with co-releated in filter - isNotEmpty" to kt(
      "Table(Person).filter { p -> Table(Address).filter { a -> a.ownerId == p.id }.isNotEmpty_MC() }"
    ),
    "query with co-releated in filter - isNotEmpty" to cr(
      "SELECT p.id AS id, p.name AS name, p.age AS age FROM Person p WHERE EXISTS (SELECT a.ownerId AS ownerId, a.street AS street, a.city AS city FROM Address a WHERE a.ownerId = p.id)"
    ),
    "query with co-releated in filter - isEmpty/XR" to kt(
      "Table(Person).filter { p -> Table(Address).filter { a -> a.ownerId == p.id }.isEmpty_MC() }"
    ),
    "query with co-releated in filter - isEmpty" to cr(
      "SELECT p.id AS id, p.name AS name, p.age AS age FROM Person p WHERE NOT EXISTS (SELECT a.ownerId AS ownerId, a.street AS street, a.city AS city FROM Address a WHERE a.ownerId = p.id)"
    ),
  )
}
