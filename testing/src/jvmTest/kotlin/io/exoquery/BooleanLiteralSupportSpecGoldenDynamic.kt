package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object BooleanLiteralSupportSpecGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "value-fy boolean expression where needed/condition/XR" to kt(
      "Table(Ent).map { e -> Tuple(first = e.name, second = if (e.b == e.bb) e.bc else e.b == e.bb) }"
    ),
    "value-fy boolean expression where needed/condition/SQL" to cr(
      "SELECT e.name AS first, CASE WHEN e.b = e.bb THEN e.bc ELSE CASE WHEN e.b = e.bb THEN true ELSE false END END AS second FROM Ent e"
    ),
  )
}
