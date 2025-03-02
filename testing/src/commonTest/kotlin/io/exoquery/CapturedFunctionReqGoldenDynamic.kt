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
    "static function capture - structural tests/val tbl; select { from(capFun(tbl)) }/XR" to kt(
      "select { val p = from({ people -> people.filter { p -> p.name == Joe } }.toQuery.apply(Table(Person))) }"
    ),
    "static function capture - structural tests/val tbl; select { from(capFun(tbl)) }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe'"
    ),
    "static function capture - structural tests/cap { capFunA { capFunB } }/XR" to kt(
      "{ people -> { people -> people.filter { p -> p.name == Joe } }.toQuery.apply(people.filter { p -> p.name == Jack }) }.toQuery.apply(Table(Person))"
    ),
    "static function capture - structural tests/cap { capFunA { capFunB } }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Jack' AND p.name = 'Joe'"
    ),
    "static function capture - structural tests/cap { capFunA(x) -> capFunB }/XR" to kt(
      "{ people, name -> { people -> people.filter { p -> p.name == Joe } }.toQuery.apply(people.filter { p -> p.name == name }) }.toQuery.apply(Table(Person), Jack)"
    ),
    "static function capture - structural tests/cap { capFunA(x) -> capFunB }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Jack' AND p.name = 'Joe'"
    ),
    "static function capture - structural tests/cap { capFunA(x) -> capFunB(x) -> capFunC }/XR" to kt(
      "{ people, name -> { people, name -> { people -> people.filter { p -> p.name == Joe } }.toQuery.apply(people.filter { p -> p.name == name }) }.toQuery.apply(people, name) }.toQuery.apply(Table(Person), Jack)"
    ),
    "static function capture - structural tests/cap { capFunA(x) -> capFunB(x) -> capFunC }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Jack' AND p.name = 'Joe'"
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
