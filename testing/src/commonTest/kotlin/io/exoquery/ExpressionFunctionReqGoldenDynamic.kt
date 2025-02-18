package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object ExpressionFunctionReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "String/toInt" to cr(
      "SELECT CAST(p.name AS INTEGER) AS value FROM Person p"
    ),
    "String/toLong" to cr(
      "SELECT CAST(p.name AS BIGINT) AS value FROM Person p"
    ),
    "String/toBoolean" to cr(
      "SELECT CAST(p.name AS BOOLEAN) AS value FROM Person p"
    ),
    "Int/toLong" to cr(
      "SELECT CAST(p.age AS BIGINT) AS value FROM Person p"
    ),
    "Int/toDouble" to cr(
      "SELECT CAST(p.age AS DOUBLE PRECISION) AS value FROM Person p"
    ),
    "Int/toFloat" to cr(
      "SELECT CAST(p.age AS REAL) AS value FROM Person p"
    ),
    "Long/toInt" to cr(
      "SELECT CAST(p.age AS INTEGER) AS value FROM Person p"
    ),
    "Long/toDouble" to cr(
      "SELECT CAST(p.age AS DOUBLE PRECISION) AS value FROM Person p"
    ),
    "Long/toFloat" to cr(
      "SELECT CAST(p.age AS REAL) AS value FROM Person p"
    ),
    "Float/toInt" to cr(
      "SELECT CAST(p.age AS INTEGER) AS value FROM Person p"
    ),
    "Float/toLong" to cr(
      "SELECT CAST(p.age AS BIGINT) AS value FROM Person p"
    ),
    "Float/toDouble" to cr(
      "SELECT CAST(p.age AS DOUBLE PRECISION) AS value FROM Person p"
    ),
  )
}
