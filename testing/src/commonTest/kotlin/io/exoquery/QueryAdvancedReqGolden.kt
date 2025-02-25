package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object QueryAdvancedReqGolden: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "select clause + join + nested filters" to cr(
      """
      SELECT
        p.id,
        p.name,
        p.age,
        a.ownerId,
        a.street,
        a.city
      FROM
        Person p
        INNER JOIN (
          SELECT
            a.ownerId,
            a.street,
            a.city
          FROM
            Address a
          WHERE
            a.street = '123 St.'
        ) AS a ON a.ownerId = p.id
      WHERE
        p.age > 18
      """
    ),
    "select clauses from(nested)" to cr(
      """
      SELECT
        r.ownerId,
        r.name,
        r.model
      FROM
        Person p,
        (
          SELECT
            p.id AS first_id,
            p.name AS first_name,
            p.age AS first_age,
            a.ownerId AS second_ownerId,
            a.street AS second_street,
            a.city AS second_city
          FROM
            INNER JOIN Address a ON a.ownerId = p.id
        ) AS a
        INNER JOIN Robot r ON r.ownerId = a.first_id
      """
    ),
  )
}
