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
      "SELECT e.bb = 1 AS value FROM Ent e"
    ),
    "value-fy boolean expression where needed/map-clause with int/XR" to kt(
      "Table(Ent).map { e -> e.num > 10 }"
    ),
    "value-fy boolean expression where needed/map-clause with int/SQL" to cr(
      "SELECT e.num > 10 AS value FROM Ent e"
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
      "SELECT e.name, e.i, e.b FROM Ent e WHERE CASE WHEN e.i > 123 THEN 1 ELSE 0 END"
    ),
    "sql/expressify asCondition/pure filter-clause/XR" to kt(
      """Table(Ent).filter { e -> free(", ${'$'}{e.i},  > 123").asPureCondition() }"""
    ),
    "sql/expressify asCondition/pure filter-clause/SQL" to cr(
      "SELECT e.name, e.i, e.b FROM Ent e WHERE CASE WHEN e.i > 123 THEN 1 ELSE 0 END"
    ),
    "sql/expressify asCondition/map-clause/XR" to kt(
      """Table(Ent).map { e -> free(", ${'$'}{e.i},  > 123").asCondition() }"""
    ),
    "sql/expressify asCondition/map-clause/SQL" to cr(
      "SELECT e.i > 123 AS value FROM Ent e"
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
      "SELECT e.i > 123 AS value FROM Ent e"
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
      "SELECT r.value AS first, r.value AS second FROM (SELECT DISTINCT e.i > 123 AS value FROM Ent e) AS r"
    ),
    "sql/valuefy normally/filter-clause/XR" to kt(
      """Table(Ent).filter { e -> free("SomeUdf(, ${'$'}{e.i}, )").invoke() }"""
    ),
    "sql/valuefy normally/filter-clause/SQL" to cr(
      "SELECT e.name, e.i, e.b FROM Ent e WHERE SomeUdf(e.i)"
    ),
    "sql/valuefy normally/map-clause/XR" to kt(
      """Table(Ent).map { e -> free("SomeUdf(, ${'$'}{e.i}, )").invoke() }.map { x -> x + 1 }"""
    ),
    "sql/valuefy normally/map-clause/SQL" to cr(
      "SELECT e + 1 AS value FROM Ent e"
    ),
    "do not expressify string transforming operations/first parameter/XR" to kt(
      """Table(Product).filter { p -> TagP("2a5ca").toInt_MC() == p.sku }"""
    ),
    "do not expressify string transforming operations/first parameter/SQL" to cr(
      "SELECT p.id, p.desc, p.sku FROM Product p WHERE CASE WHEN CAST({0:1} AS INTEGER) = p.sku THEN 1 ELSE 0 END",
      "56742506-e718-44a1-b419-4b4c75a2a5ca" to "1"
    ),
    "do not expressify string transforming operations/second parameter/XR" to kt(
      """Table(Product).filter { p -> p.sku == TagP("72295").toInt_MC() }"""
    ),
    "do not expressify string transforming operations/second parameter/SQL" to cr(
      "SELECT p.id, p.desc, p.sku FROM Product p WHERE CASE WHEN p.sku = CAST({0:1} AS INTEGER) THEN 1 ELSE 0 END",
      "1dd84ad8-ced5-450f-a356-5db9e3d72295" to "1"
    ),
    "do not expressify string transforming operations/both parameters/XR" to kt(
      """Table(Product).filter { p -> TagP("b17ca").toInt_MC() == TagP("acebd").toInt_MC() }"""
    ),
    "do not expressify string transforming operations/both parameters/SQL" to cr(
      "SELECT p.id, p.desc, p.sku FROM Product p WHERE CASE WHEN CAST({0:2} AS INTEGER) = CAST({1:1} AS INTEGER) THEN 1 ELSE 0 END",
      "8096c196-eb8a-4ca2-87b5-1c6f3eab17ca" to "2", "dba34c71-1c77-4313-a5ca-195dd20acebd" to "1"
    ),
    "joins/for-comprehension with constant/XR" to kt(
      "select { val t1 = from(Table(TestEntity)); val t2 = join(Table(TestEntity)) { true } }"
    ),
    "joins/for-comprehension with constant/SQL" to cr(
      "SELECT t1.s, t1.i, t1.l, t1.o, t1.b, t.s, t.i, t.l, t.o, t.b FROM TestEntity t1 INNER JOIN TestEntity t ON 1"
    ),
    "joins/for-comprehension with field/XR" to kt(
      "select { val t1 = from(Table(TestEntity)); val t2 = join(Table(TestEntity)) { t.b } }"
    ),
    "joins/for-comprehension with field/SQL" to cr(
      "SELECT t1.s, t1.i, t1.l, t1.o, t1.b, t.s, t.i, t.l, t.o, t.b FROM TestEntity t1 INNER JOIN TestEntity t ON t.b"
    ),
    "optionals/exists/XR" to kt(
      "Table(TestEntity).filter { t -> if ({ val tmp0_elvis_lhs = t.o; if (tmp0_elvis_lhs == null) false else tmp0_elvis_lhs }) false else true }.map { t -> Tuple(first = t.b, second = true) }"
    ),
    "optionals/exists/SQL" to cr(
      "SELECT t.b AS first, 1 AS second FROM TestEntity t WHERE (t.o IS NULL AND 1 = 0 OR NOT (t.o IS NULL) AND 1 = t.o) AND 1 = 0 OR NOT (t.o IS NULL AND 1 = 0 OR NOT (t.o IS NULL) AND 1 = t.o) AND 1 = 1"
    ),
    "optionals/exists - lifted/XR" to kt(
      """Table(TestEntity).filter { t -> { val tmp1_elvis_lhs = { val tmp0_safe_receiver = t.o; if (tmp0_safe_receiver == null) null else { it -> if (TagP("3639a")) TagP("d0ac6") else TagP("595ed") }.apply(tmp0_safe_receiver) }; if (tmp1_elvis_lhs == null) false else tmp1_elvis_lhs } }.map { t -> Tuple(first = t.b, second = true) }"""
    ),
    "optionals/exists - lifted/SQL" to cr(
      "SELECT t.b AS first, 1 AS second FROM TestEntity t WHERE CASE WHEN 1 = {8:false} THEN {7:false} ELSE {9:true} END IS NULL AND 1 = 0 OR NOT (CASE WHEN 1 = {8:false} THEN {7:false} ELSE {9:true} END IS NULL) AND (1 = {8:false} AND 1 = {7:false} OR NOT (1 = {8:false}) AND 1 = {9:true})",
      "d6898f53-a22e-4f50-9c90-85e347d3639a" to "false", "ca0c3d81-4241-4fd2-9884-29e3a88d0ac6" to "false", "606846fc-6e58-4981-a1d2-335702a595ed" to "true", "d6898f53-a22e-4f50-9c90-85e347d3639a" to "false", "ca0c3d81-4241-4fd2-9884-29e3a88d0ac6" to "false", "606846fc-6e58-4981-a1d2-335702a595ed" to "true", "d6898f53-a22e-4f50-9c90-85e347d3639a" to "false", "ca0c3d81-4241-4fd2-9884-29e3a88d0ac6" to "false", "d6898f53-a22e-4f50-9c90-85e347d3639a" to "false", "606846fc-6e58-4981-a1d2-335702a595ed" to "true"
    ),
  )
}
