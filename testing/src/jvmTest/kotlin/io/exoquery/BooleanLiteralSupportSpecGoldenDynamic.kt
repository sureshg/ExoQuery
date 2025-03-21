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
      "SELECT e.name AS first, CASE WHEN e.b = e.bb THEN e.bc ELSE CASE WHEN e.b = e.bb THEN 1 ELSE 0 END END AS second FROM Ent e"
    ),
    "value-fy boolean expression where needed/map-clause/XR" to kt(
      "Table(Ent).map { e -> e.bb == true }"
    ),
    "value-fy boolean expression where needed/map-clause/SQL" to cr(
      "SELECT CASE WHEN e.bb = 1 THEN 1 ELSE 0 END AS value FROM Ent e"
    ),
    "value-fy boolean expression where needed/map-clause with int/XR" to kt(
      "Table(Ent).map { e -> e.num > 10 }"
    ),
    "value-fy boolean expression where needed/map-clause with int/SQL" to cr(
      "SELECT CASE WHEN e.num > 10 THEN 1 ELSE 0 END AS value FROM Ent e"
    ),
    "value-fy boolean expression where needed/tuple/XR" to kt(
      "Table(Ent).map { e -> Tuple(first = foo, second = e.bb == true) }"
    ),
    "value-fy boolean expression where needed/tuple/SQL" to cr(
      "SELECT 'foo' AS first, CASE WHEN e.bb = 1 THEN 1 ELSE 0 END AS second FROM Ent e"
    ),
    "value-fy boolean expression where needed/tuple-multi/XR" to kt(
      "Table(Ent).map { e -> Tuple(first = Tuple(first = e.bb == true, second = e.bc == false), second = e.num > 1) }"
    ),
    "value-fy boolean expression where needed/tuple-multi/SQL" to cr(
      "SELECT CASE WHEN e.bb = 1 THEN 1 ELSE 0 END AS first, CASE WHEN e.bc = 0 THEN 1 ELSE 0 END AS second, CASE WHEN e.num > 1 THEN 1 ELSE 0 END AS second FROM Ent e"
    ),
    "value-fy boolean expression where needed/case-class/XR" to kt(
      "Table(Ent).map { e -> Status(name = foo, value = e.bb == true) }"
    ),
    "value-fy boolean expression where needed/case-class/SQL" to cr(
      "SELECT 'foo' AS name, CASE WHEN e.bb = 1 THEN 1 ELSE 0 END AS value FROM Ent e"
    ),
    "sql/expressify asCondition/filter-clause/XR" to kt(
      """Table(Ent).filter { e -> free(", ${'$'}{e.i},  > 123").asCondition() }"""
    ),
    "sql/expressify asCondition/filter-clause/SQL" to cr(
      "SELECT e.name, e.i, e.b FROM Ent e WHERE e.i > 123"
    ),
    "sql/expressify asCondition/pure filter-clause/XR" to kt(
      """Table(Ent).filter { e -> free(", ${'$'}{e.i},  > 123").asPureCondition() }"""
    ),
    "sql/expressify asCondition/pure filter-clause/SQL" to cr(
      "SELECT e.name, e.i, e.b FROM Ent e WHERE e.i > 123"
    ),
    "sql/expressify asCondition/map-clause/XR" to kt(
      """Table(Ent).map { e -> free(", ${'$'}{e.i},  > 123").asCondition() }"""
    ),
    "sql/expressify asCondition/map-clause/SQL" to cr(
      "SELECT CASE WHEN e.i > 123 THEN 1 ELSE 0 END AS value FROM Ent e"
    ),
    "sql/expressify asCondition/distinct map-clause/XR" to kt(
      """Table(Ent).map { e -> Tuple(first = foo, second = free(", ${'$'}{e.i},  > 123").asCondition()) }.distinct.map { r -> Tuple(first = baz, second = r.second) }"""
    ),
    "sql/expressify asCondition/distinct map-clause/SQL" to cr(
      "SELECT 'baz' AS first, CASE WHEN r.second THEN 1 ELSE 0 END AS second FROM (SELECT DISTINCT 'foo' AS first, CASE WHEN e.i > 123 THEN 1 ELSE 0 END AS second FROM Ent e) AS r"
    ),
    "sql/expressify asCondition/distinct tuple map-clause/XR" to kt(
      """Table(Ent).map { e -> Tuple(first = foo, second = free(", ${'$'}{e.i},  > 123").asPureCondition()) }.distinct"""
    ),
    "sql/expressify asCondition/distinct tuple map-clause/SQL" to cr(
      "SELECT DISTINCT 'foo' AS first, CASE WHEN e.i > 123 THEN 1 ELSE 0 END AS second FROM Ent e"
    ),
    "sql/expressify asCondition/pure map-clause/XR" to kt(
      """Table(Ent).map { e -> free(", ${'$'}{e.i},  > 123").asPureCondition() }"""
    ),
    "sql/expressify asCondition/pure map-clause/SQL" to cr(
      "SELECT CASE WHEN e.i > 123 THEN 1 ELSE 0 END AS value FROM Ent e"
    ),
    "sql/expressify asCondition/pure distinct map-clause/XR" to kt(
      """Table(Ent).map { e -> Tuple(first = foo, second = free(", ${'$'}{e.i},  > 123").asPureCondition()) }.distinct.map { r -> Tuple(first = baz, second = r.second) }"""
    ),
    "sql/expressify asCondition/pure distinct map-clause/SQL" to cr(
      "SELECT 'baz' AS first, CASE WHEN r.second THEN 1 ELSE 0 END AS second FROM (SELECT DISTINCT 'foo' AS first, CASE WHEN e.i > 123 THEN 1 ELSE 0 END AS second FROM Ent e) AS r"
    ),
    "sql/expressify asCondition/pure map-clause - double element/XR" to kt(
      """Table(Ent).map { e -> free(", ${'$'}{e.i},  > 123").asPureCondition() }.distinct.map { r -> Tuple(first = r, second = r) }"""
    ),
    "sql/expressify asCondition/pure map-clause - double element/SQL" to cr(
      "SELECT r.value AS first, r.value AS second FROM (SELECT DISTINCT CASE WHEN e.i > 123 THEN 1 ELSE 0 END AS value FROM Ent e) AS r"
    ),
    "sql/valuefy normally/filter-clause/XR" to kt(
      """Table(Ent).filter { e -> free("SomeUdf(, ${'$'}{e.i}, )").invoke() }"""
    ),
    "sql/valuefy normally/filter-clause/SQL" to cr(
      "SELECT e.name, e.i, e.b FROM Ent e WHERE 1 = SomeUdf(e.i)"
    ),
    "sql/valuefy normally/map-clause/XR" to kt(
      """Table(Ent).map { e -> free("SomeUdf(, ${'$'}{e.i}, )").invoke() }.map { x -> x + 1 }"""
    ),
    "sql/valuefy normally/map-clause/SQL" to cr(
      "SELECT e + 1 AS value FROM Ent e"
    ),
  )
}
