package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object ActionReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "insert/simple/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }"
    ),
    "insert/simple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123)"
    ),
    "insert/simple with params/XR" to kt(
      """insert<Person> { set(thisinsert.name to TagP("0"), thisinsert.age to TagP("1")) }"""
    ),
    "insert/simple with params/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123})",
      "0" to "Joe", "1" to "123"
    ),
    "insert/simple with setParams/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }"""
    ),
    "insert/simple with setParams/SQL" to cr(
      "INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123})",
      "0" to "1", "1" to "Joe", "2" to "123"
    ),
    "insert/simple with setParams and exclusion/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id)) }"""
    ),
    "insert/simple with setParams and exclusion/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123})",
      "1" to "Joe", "2" to "123"
    ),
    "insert/simple with setParams and exclusion - multiple/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id, thisinsert.name)) }"""
    ),
    "insert/simple with setParams and exclusion - multiple/SQL" to cr(
      "INSERT INTO Person (age) VALUES ({0:123})",
      "2" to "123"
    ),
    "insert/with returning/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.returning { p -> p.id }"
    ),
    "insert/with returning/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) RETURNING id"
    ),
    "insert/with returning/returningType" to cr(
      "ClauseInQuery"
    ),
    "insert/with returning - multiple/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.returning { p -> Tuple(first = p.id, second = p.name) }"
    ),
    "insert/with returning - multiple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) RETURNING id, name"
    ),
    "insert/with returning - multiple/returningType" to cr(
      "ClauseInQuery"
    ),
    "insert/with returning params/XR" to kt(
      """insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.returning { p -> Tuple(first = p.name, second = TagP("0")) }"""
    ),
    "insert/with returning params/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) RETURNING name, {0:myParamValue}",
      "0" to "myParamValue"
    ),
    "insert/with returning params/returningType" to cr(
      "ClauseInQuery"
    ),
    "insert/with returningKeys/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.returningKeys { listOf(thisreturningKeys.id) }"
    ),
    "insert/with returningKeys/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123)"
    ),
    "insert/with returningKeys/returningType" to cr(
      "Keys(columns=[id])"
    ),
    "insert/with returningKeys - multiple/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.returningKeys { listOf(thisreturningKeys.id, thisreturningKeys.name) }"
    ),
    "insert/with returningKeys - multiple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123)"
    ),
    "insert/with returningKeys - multiple/returningType" to cr(
      "Keys(columns=[id, name])"
    ),
    "update/simple/XR" to kt(
      "update<Person> { set(thisupdate.name to Joe, thisupdate.age to 123) }.filter { p -> p.id == 1 }"
    ),
    "update/simple/SQL" to cr(
      "UPDATE Person SET name = 'Joe', age = 123 WHERE id = 1"
    ),
    "update/no condition/XR" to kt(
      "update<Person> { set(thisupdate.name to Joe, thisupdate.age to 123) }"
    ),
    "update/no condition/SQL" to cr(
      "UPDATE Person SET name = 'Joe', age = 123"
    ),
    "update/with setParams/XR" to kt(
      """update<Person> { set(thisupdate.id to TagP("0"), thisupdate.name to TagP("1"), thisupdate.age to TagP("2")) }.filter { p -> p.id == 1 }"""
    ),
    "update/with setParams/SQL" to cr(
      "UPDATE Person SET id = {0:1}, name = {1:Joe}, age = {2:123} WHERE id = 1",
      "0" to "1", "1" to "Joe", "2" to "123"
    ),
    "update/with setParams and exclusion/XR" to kt(
      """update<Person> { set(thisupdate.id to TagP("0"), thisupdate.name to TagP("1"), thisupdate.age to TagP("2")).excluding(listOf(thisupdate.id)) }.filter { p -> p.id == 1 }"""
    ),
    "update/with setParams and exclusion/SQL" to cr(
      "UPDATE Person SET name = {0:Joe}, age = {1:123} WHERE id = 1",
      "1" to "Joe", "2" to "123"
    ),
    "update/with returning/XR" to kt(
      "update<Person> { set(thisupdate.name to Joe, thisupdate.age to 123) }.filter { p -> p.id == 1 }.returning { p -> p.id }"
    ),
    "update/with returning/SQL" to cr(
      "UPDATE Person SET name = 'Joe', age = 123 WHERE id = 1 RETURNING id"
    ),
    "update/with returning/returningType" to cr(
      "ClauseInQuery"
    ),
    "update/with returningKeys/XR" to kt(
      "update<Person> { set(thisupdate.name to Joe, thisupdate.age to 123) }.filter { p -> p.id == 1 }.returningKeys { listOf(thisreturningKeys.id) }"
    ),
    "update/with returningKeys/SQL" to cr(
      "UPDATE Person SET name = 'Joe', age = 123 WHERE id = 1"
    ),
    "update/with returningKeys/returningType" to cr(
      "Keys(columns=[id])"
    ),
    "delete/simple/XR" to kt(
      "delete<Person>.filter { p -> p.id == 1 }"
    ),
    "delete/simple/SQL" to cr(
      "DELETE FROM Person WHERE id = 1"
    ),
    "delete/no condition/XR" to kt(
      "delete<Person>"
    ),
    "delete/no condition/SQL" to cr(
      "DELETE FROM Person"
    ),
    "delete/with returning/XR" to kt(
      "delete<Person>.filter { p -> p.id == 1 }.returning { p -> p.id }"
    ),
    "delete/with returning/SQL" to cr(
      "DELETE FROM Person WHERE id = 1 RETURNING id"
    ),
    "delete/with returning/returningType" to cr(
      "ClauseInQuery"
    ),
    "delete/with returningKeys/XR" to kt(
      "delete<Person>.filter { p -> p.id == 1 }.returningKeys { listOf(thisreturningKeys.id) }"
    ),
    "delete/with returningKeys/SQL" to cr(
      "DELETE FROM Person WHERE id = 1"
    ),
    "delete/with returningKeys/returningType" to cr(
      "Keys(columns=[id])"
    ),
  )
}
