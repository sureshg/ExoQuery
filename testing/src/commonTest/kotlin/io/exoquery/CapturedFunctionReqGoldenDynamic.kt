package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object CapturedFunctionReqGoldenDynamic : GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
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
    "static function capture - structural tests/cap { capFunA(capFunB(x)) -> capFunC }/XR" to kt(
      "{ people, name -> people.filter { p -> p.name == name } }.toQuery.apply({ people, name -> people.filter { p -> p.name == name } }.toQuery.apply(Table(Person), Joe), Jack)"
    ),
    "static function capture - structural tests/cap { capFunA(capFunB(x)) -> capFunC }/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe' AND p.name = 'Jack'"
    ),
    "advanced cases/passing in a param/XR" to kt(
      """{ people, otherValue, f -> select { val p = from(people); val a = join(Table(Address)) { a.ownerId == f.apply(p) && a.street == otherValue } } }.toQuery.apply(Table(Person).filter { p -> p.name == TagP("1") }, TagP("0"), { it -> it.id }).map { kv -> Tuple(first = kv.first, second = kv.second) }"""
    ),
    "advanced cases/passing in a param/SQL" to cr(
      "SELECT p.id, p.name, p.age, a.ownerId, a.street, a.city FROM Person p INNER JOIN Address a ON a.ownerId = p.id AND a.street = {0:foobar} WHERE p.name = {1:joe}",
      "d313a577-9948-4602-9325-13df11371ff5" to "foobar", "4a30d14c-2e75-4934-a1ba-70a79dd63d59" to "joe"
    ),
    "advanced cases/subtype polymorphicsm/XR" to kt(
      """{ people -> select { val p = from(people); val a = join(Table(Address)) { a.ownerId == p.id } } }.toQuery.apply(Table(Person).filter { p -> p.name == TagP("0") }).map { kv -> Tuple(first = kv.first.name, second = kv.second.city) }"""
    ),
    "advanced cases/subtype polymorphicsm/SQL" to cr(
      "SELECT p.name AS first, a.city AS second FROM Person p INNER JOIN Address a ON a.ownerId = p.id WHERE p.name = {0:joe}",
      "121abcd6-1e72-4925-bcc5-668f510bb369" to "joe"
    ),
    "advanced cases/lambda polymorphism - A/XR" to kt(
      """{ people, f -> select { val p = from(people); val a = join(Table(Address)) { a.ownerId == f.apply(p) } } }.toQuery.apply(Table(Person).filter { p -> p.name == TagP("0") }, { it -> it.id }).map { kv -> Tuple(first = kv.first.name, second = kv.second.city) }"""
    ),
    "advanced cases/lambda polymorphism - A/SQL" to cr(
      "SELECT p.name AS first, a.city AS second FROM Person p INNER JOIN Address a ON a.ownerId = p.id WHERE p.name = {0:joe}",
      "4a30d14c-2e75-4934-a1ba-70a79dd63d59" to "joe"
    ),
    "advanced cases/lambda polymorphism - B/XR" to kt(
      """{ people, f -> select { val p = from(people); val a = join(Table(Address)) { f.apply(p, a) } } }.toQuery.apply(Table(Person).filter { p -> p.name == TagP("0") }, { p, a -> { p, a -> p.id == a.ownerId }.apply(p, a) }).map { kv -> Tuple(first = kv.first.name, second = kv.second.city) }"""
    ),
    "advanced cases/lambda polymorphism - B/SQL" to cr(
      "SELECT p.name AS first, a.city AS second FROM Person p INNER JOIN Address a ON p.id = a.ownerId WHERE p.name = {0:joe}",
      "4a30d14c-2e75-4934-a1ba-70a79dd63d59" to "joe"
    ),
    "advanced cases/lambda polymorphism - C + captured expression/XR" to kt(
      """{ people, f -> select { val p = from(people); val a = join(Table(Address)) { f.apply(p, a) } } }.toQuery.apply(Table(Person).filter { p -> p.name == TagP("0") }, { p, a -> { p, a -> p.id == a.ownerId }.apply(p, a) }).map { kv -> Tuple(first = kv.first.name, second = kv.second.city) }"""
    ),
    "advanced cases/lambda polymorphism - C + captured expression/SQL" to cr(
      "SELECT p.name AS first, a.city AS second FROM Person p INNER JOIN Address a ON p.id = a.ownerId WHERE p.name = {0:joe}",
      "4a30d14c-2e75-4934-a1ba-70a79dd63d59" to "joe"
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
