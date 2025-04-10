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
      "f68208a6-87d7-4b7f-bb23-a10b0081ab04" to "Joe", "ab125006-fc5a-4b74-85d4-55eec3ba0adf" to "123"
    ),
    "insert/simple with setParams/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }"""
    ),
    "insert/simple with setParams/SQL" to cr(
      "INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123})",
      "0dd8c2d5-17e0-4fba-b070-2a891890c983" to "1", "2e876f46-bfea-4f7f-87c3-69008fbf2267" to "Joe", "f3fddbb1-a4a2-4f3c-864e-f51a8c55b606" to "123"
    ),
    "insert/simple with setParams and exclusion/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id)) }"""
    ),
    "insert/simple with setParams and exclusion/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123})",
      "80d7ef32-1040-4715-9ce9-c0e0ea3aaa22" to "Joe", "7ab46d3c-e97b-4f90-b1a4-876c85b4f0ee" to "123"
    ),
    "insert/simple with setParams and exclusion - multiple/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id, thisinsert.name)) }"""
    ),
    "insert/simple with setParams and exclusion - multiple/SQL" to cr(
      "INSERT INTO Person (age) VALUES ({0:123})",
      "4ad8e3c6-679a-4843-9e4a-89b91f853c80" to "123"
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
      """insert<Person> { set(thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }.returning { p -> Tuple(first = p.name, second = TagP("0")) }"""
    ),
    "insert/with returning params/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123}) RETURNING name, {2:myParamValue}",
      "db743c00-ee55-40b1-b518-a98da6e8da5b" to "Joe", "bf025b29-e67b-4031-a751-d2e1e784651e" to "123", "49253041-d8bb-4560-b8d1-fba73434cdc0" to "myParamValue"
    ),
    "insert/with returning params/Params" to kt(
      "[ParamSingle(0, Joe, String), ParamSingle(1, 123, Int), ParamSingle(2, myParamValue, String)]"
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
      "9df1cbe1-a080-495b-9989-a3bc1f9aaa62" to "1", "68710e96-7fa3-450a-b06c-815ff887e12c" to "Joe", "f8dfb82b-4acb-45d8-b636-e1815822753a" to "123"
    ),
    "update/with setParams and exclusion/XR" to kt(
      """update<Person> { set(thisupdate.id to TagP("0"), thisupdate.name to TagP("1"), thisupdate.age to TagP("2")).excluding(listOf(thisupdate.id)) }.filter { p -> p.id == 1 }"""
    ),
    "update/with setParams and exclusion/SQL" to cr(
      "UPDATE Person SET name = {0:Joe}, age = {1:123} WHERE id = 1",
      "f3c35e3d-6e92-4188-bb60-4c20febb35f6" to "Joe", "5e33ba49-7ac5-4574-bb3f-db65735ded54" to "123"
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
