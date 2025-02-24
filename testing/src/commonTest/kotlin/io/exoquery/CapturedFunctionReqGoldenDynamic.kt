package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object CapturedFunctionReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "static function capture - structural tests/cap { capFun(Table) }/XR" to kt(
      "{ people -> people.filter { p -> p.name == Joe } }.toQuery.apply(Table(Person))"
    ),
    "static function capture - structural tests/cap { capFun(Table) }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe'"
    ),
    "static function capture - structural tests/val tbl; cap { capFun(tbl) }/XR" to kt(
      "{ people -> people.filter { p -> p.name == Joe } }.toQuery.apply(Table(Person))"
    ),
    "static function capture - structural tests/val tbl; cap { capFun(tbl) }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe'"
    ),
    "static function capture - structural tests/val tbl; cap { capFun(tbl).filter }/XR" to kt(
      "{ people -> people.filter { p -> p.name == Joe } }.toQuery.apply(Table(Person)).filter { p -> p.age > 21 }"
    ),
    "static function capture - structural tests/val tbl; cap { capFun(tbl).filter }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe' AND p.age > 21"
    ),
    "static function capture - structural tests/val tbl; select { from(tbl) }/XR" to kt(
      "select { val p = from(Table(Person)) }"
    ),
    "static function capture - structural tests/val tbl; select { from(tbl) }/SQL" to cr(
      "SELECT x.id, x.name, x.age FROM Person x"
    ),
    "static function capture - structural tests/val tbl; select { from(tbl); join }/XR" to kt(
      "select { val p = from(Table(Person)); val a = join(Table(Address)) { a.ownerId == p.id } }"
    ),
    "static function capture - structural tests/val tbl; select { from(tbl); join }/SQL" to cr(
      "SELECT p.id, p.name, p.age, a.ownerId, a.street, a.zip FROM Person p INNER JOIN Address a ON a.ownerId = p.id"
    ),
    "dynamic function capture - structural tests/val tbl(Dyn); cap { capFun(tbl) }/XR-tbl" to kt(
      "Table(Person)"
    ),
    "dynamic function capture - structural tests/val tbl(Dyn); cap { capFun(tbl) }/XR" to kt(
      """{ people -> people.filter { p -> p.name == Joe } }.toQuery.apply(TagQ("0"))"""
    ),
    "dynamic function capture - structural tests/val tbl(Dyn); cap { capFun(tbl) }/XR->Runimtes.first.XR" to kt(
      "Table(Person)"
    ),
    "dynamic function capture - structural tests/val tbl(Dyn); cap { capFun(tbl) }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe'"
    ),
  )
}
