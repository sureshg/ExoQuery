package io.exoquery

import io.exoquery.printing.cr

object BasicQuerySanitySpecGolden: GoldenQueryFile {
  override val queries = mapOf(
    "basic query" to cr(
      "SELECT p.id AS id, p.name AS name, p.age AS age FROM Person p WHERE p.name = 'Joe'"
    ),
    "query with join" to cr(
      """
      SELECT
        p.id AS id,
        p.name AS name,
        p.age AS age,
        a.ownerId AS ownerId,
        a.street AS street,
        a.city AS city
      FROM
        Person p
        INNER JOIN Address a ON a.ownerId = p.id
      """
    ),
  )
}