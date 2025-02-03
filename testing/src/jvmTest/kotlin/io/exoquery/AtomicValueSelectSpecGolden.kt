package io.exoquery

import io.exoquery.printing.cr

object AtomicValueSelectSpecGolden: GoldenQueryFile {
  override val queries = mapOf(
    "from + join -> (p, r)" to cr(
      """
      SELECT
        p.name AS first,
        r.ownerId AS ownerId,
        r.model AS model
      FROM
        Person p
        INNER JOIN Robot r ON p.name = r.model
      """
    ),
  )
}
