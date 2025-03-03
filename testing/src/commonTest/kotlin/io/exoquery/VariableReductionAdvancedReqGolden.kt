package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object VariableReductionAdvancedReqGolden: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "in simple case" to cr(
      """
      SELECT
        p.name,
        a.city
      FROM
        Person p
        INNER JOIN Address a ON p.id = a.ownerId
      """
    ),
    "with leaf-level props" to cr(
      """
      SELECT
        destruct.id AS first,
        address.city AS second
      FROM
        Person destruct
        INNER JOIN Address address ON destruct.id = address.ownerId
      """
    ),
    "when passed to further join" to cr(
      """
      SELECT
        a.first_name AS name,
        a.second_city AS city,
        r.name AS robotName
      FROM
        (
          SELECT
            p.id AS first_id,
            p.name AS first_name,
            p.age AS first_age,
            a.ownerId AS second_ownerId,
            a.street AS second_street,
            a.city AS second_city
          FROM
            Person p
            INNER JOIN Address a ON p.id = a.ownerId
        ) AS a
        INNER JOIN Robot r ON a.first_id = r.ownerId
      """
    ),
  )
}
