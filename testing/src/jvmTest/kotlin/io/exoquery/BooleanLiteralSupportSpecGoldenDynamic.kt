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
      "SELECT 1 = e.bb AS value FROM Ent e"
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
      "SELECT 'foo' AS first, e.bb AS second FROM Ent e"
    ),
    "value-fy boolean expression where needed/tuple-multi/XR" to kt(
      "Table(Ent).map { e -> Tuple(first = Tuple(first = e.bb == true, second = e.bc == false), second = e.num > 1) }"
    ),
    "value-fy boolean expression where needed/tuple-multi/SQL" to cr(
      "SELECT e.bb AS first, CASE WHEN e.bc = 0 THEN 1 ELSE 0 END AS second, CASE WHEN e.num > 1 THEN 1 ELSE 0 END AS second FROM Ent e"
    ),
    "value-fy boolean expression where needed/case-class/XR" to kt(
      "Table(Ent).map { e -> Status(name = foo, value = e.bb == true) }"
    ),
    "value-fy boolean expression where needed/case-class/SQL" to cr(
      "SELECT 'foo' AS name, e.bb AS value FROM Ent e"
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
      """Table(Product).filter { p -> TagP("ad98b").toInt_MC() == p.sku }"""
    ),
    "do not expressify string transforming operations/first parameter/SQL" to cr(
      "SELECT p.id, p.desc, p.sku FROM Product p WHERE CASE WHEN CAST({0:1} AS INTEGER) = p.sku THEN 1 ELSE 0 END",
      "a803f7d0-7d85-4eae-bc2e-8eb5b5cad98b" to "1"
    ),
    "do not expressify string transforming operations/second parameter/XR" to kt(
      """Table(Product).filter { p -> p.sku == TagP("fd3e6").toInt_MC() }"""
    ),
    "do not expressify string transforming operations/second parameter/SQL" to cr(
      "SELECT p.id, p.desc, p.sku FROM Product p WHERE CASE WHEN p.sku = CAST({0:1} AS INTEGER) THEN 1 ELSE 0 END",
      "f89c60fb-f634-4f9a-8ecc-e01d5d4fd3e6" to "1"
    ),
    "do not expressify string transforming operations/both parameters/XR" to kt(
      """Table(Product).filter { p -> TagP("6ae19").toInt_MC() == TagP("83f60").toInt_MC() }"""
    ),
    "do not expressify string transforming operations/both parameters/SQL" to cr(
      "SELECT p.id, p.desc, p.sku FROM Product p WHERE CASE WHEN CAST({0:2} AS INTEGER) = CAST({1:1} AS INTEGER) THEN 1 ELSE 0 END",
      "0315a9fa-28af-479a-a19a-95c33a66ae19" to "2", "045f248e-ad96-4d3d-b83c-83eb5c783f60" to "1"
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
      "SELECT t.b AS first, 1 AS second FROM TestEntity t WHERE CASE WHEN NOT (t.o IS NULL AND 1 = 0 OR t.o IS NOT NULL AND 1 = t.o) THEN 1 ELSE 0 END"
    ),
    "optionals/exists - lifted contains/XR" to kt(
      "Table(TestEntity).filter { t -> { val tmp1_elvis_lhs = { val tmp0_safe_receiver = t.o; if (tmp0_safe_receiver == null) null else { it -> it == true }.apply(tmp0_safe_receiver) }; if (tmp1_elvis_lhs == null) false else tmp1_elvis_lhs } }.map { t -> Tuple(first = t.b, second = true) }"
    ),
    "optionals/exists - lifted contains/SQL" to cr(
      "SELECT t.b AS first, 1 AS second FROM TestEntity t WHERE t.o IS NULL AND 1 = 0 OR t.o IS NOT NULL AND 1 = t.o"
    ),
    "optionals/exists - lifted not contains/XR" to kt(
      """Table(TestEntity).filter { t -> { val tmp1_elvis_lhs = { val tmp0_safe_receiver = t.o; if (tmp0_safe_receiver == null) null else { it -> TagP("e24fc") }.apply(tmp0_safe_receiver) }; if (tmp1_elvis_lhs == null) false else tmp1_elvis_lhs } }.map { t -> Tuple(first = t.b, second = true) }"""
    ),
    "optionals/exists - lifted not contains/SQL" to cr(
      "SELECT t.b AS first, 1 AS second FROM TestEntity t WHERE CASE WHEN CASE WHEN t.o IS NULL THEN null ELSE {1:true} END IS NULL THEN 0 ELSE CASE WHEN t.o IS NULL THEN null ELSE {1:true} END END",
      "011f679a-0dc0-46c3-86b3-0ff11e6e24fc" to "true", "011f679a-0dc0-46c3-86b3-0ff11e6e24fc" to "true"
    ),
    "optionals/exists - lifted complex/XR" to kt(
      """Table(TestEntity).filter { t -> { val tmp1_elvis_lhs = { val tmp0_safe_receiver = t.o; if (tmp0_safe_receiver == null) null else { it -> if (TagP("a7d8c")) TagP("db5c4") else TagP("dceef") }.apply(tmp0_safe_receiver) }; if (tmp1_elvis_lhs == null) false else tmp1_elvis_lhs } }.map { t -> Tuple(first = t.b, second = true) }"""
    ),
    "optionals/exists - lifted complex/SQL" to cr(
      "SELECT t.b AS first, 1 AS second FROM TestEntity t WHERE CASE WHEN CASE WHEN t.o IS NULL THEN null ELSE CASE WHEN 1 = {3:false} THEN {4:false} ELSE {5:true} END END IS NULL THEN 0 ELSE CASE WHEN t.o IS NULL THEN null ELSE CASE WHEN 1 = {3:false} THEN {4:false} ELSE {5:true} END END END",
      "95c65aed-6266-48eb-86d8-8d9a4bea7d8c" to "false", "f4a09cd0-779e-4ee2-9b34-bc7fefcdb5c4" to "false", "8046b013-023d-4f9e-9e6f-08366fbdceef" to "true", "95c65aed-6266-48eb-86d8-8d9a4bea7d8c" to "false", "f4a09cd0-779e-4ee2-9b34-bc7fefcdb5c4" to "false", "8046b013-023d-4f9e-9e6f-08366fbdceef" to "true"
    ),
  )
}
