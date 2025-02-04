package io.exoquery

import io.exoquery.printing.cr

object AtomicValueSelectSpecGolden: GoldenQueryFile {
  override val queries = mapOf(
    "from(atom.nested) + join -> (p, r)" to cr(
      """
      SELECT
        n.value AS first,
        r.ownerId AS ownerId,
        r.model AS model
      FROM
        (
          SELECT
            p.name AS value
          FROM
            Person p
        ) AS n
        INNER JOIN Robot r ON n.value = r.model
      """
    ),
    "from(atom.nested.nested) + join -> (p, r)" to cr(
      """
      SELECT
        n.value AS first,
        r.ownerId AS ownerId,
        r.model AS model
      FROM
        (
          SELECT
            x.value AS value
          FROM
            (
              SELECT
                p.name AS value
              FROM
                Person p
            ) AS x
        ) AS n
        INNER JOIN Robot r ON n.value = r.model
      """
    ),
    "groupBy(n.nested) -> join(n)" to cr(
      """
      SELECT
        n.value AS first,
        a.ownerId AS ownerId,
        a.street AS street,
        a.zip AS zip
      FROM
        (
          SELECT
            n.value_value AS value
          FROM
            (
              SELECT
                p.name AS value
              FROM
                Person p
            ) AS n
          GROUP BY
            n.value_value
        ) AS n
        INNER JOIN Address a ON n.value = a.street
      """
    ),
  )
}
