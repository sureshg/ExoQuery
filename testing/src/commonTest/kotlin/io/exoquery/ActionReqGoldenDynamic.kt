package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object ActionReqGoldenDynamic : GoldenQueryFile {
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
      "e77e4a98-c634-48b1-8e9f-fb67113ac132" to "Joe", "107a1be7-64dc-4be7-9896-cb60e599468d" to "123"
    ),
    "insert/simple with setParams/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }"""
    ),
    "insert/simple with setParams/SQL" to cr(
      "INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123})",
      "f79791b4-8c58-4022-8815-10ebf7a77f73" to "1",
      "5272f227-e734-44b4-bcdb-ffc0b9109d91" to "Joe",
      "d4e1a3a1-532c-4cea-a535-9a7b347d116d" to "123"
    ),
    "insert/simple with setParams and exclusion/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id)) }"""
    ),
    "insert/simple with setParams and exclusion/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123})",
      "497f47cb-9ec9-4f7f-819b-6084e7a7e8e5" to "Joe", "26d83e2d-635c-470f-9615-f57d2c908773" to "123"
    ),
    "insert/simple with setParams and exclusion - multiple/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id, thisinsert.name)) }"""
    ),
    "insert/simple with setParams and exclusion - multiple/SQL" to cr(
      "INSERT INTO Person (age) VALUES ({0:123})",
      "2bb2776e-99b9-4981-9ac0-2f9e4413afc7" to "123"
    ),
    "insert/with returning/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.returning { p -> p.id }"
    ),
    "insert/with returning/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) RETURNING id"
    ),
    "insert/with returning/SQL-SqlServer" to cr(
      "INSERT INTO Person (name, age) OUTPUT INSERTED.id VALUES ('Joe', 123)"
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
    "insert/with returning - multiple/SQL-SqlServer" to cr(
      "INSERT INTO Person (name, age) OUTPUT INSERTED.id, INSERTED.name VALUES ('Joe', 123)"
    ),
    "insert/with returning - multiple/returningType" to cr(
      "ClauseInQuery"
    ),
    "insert/with returning params/XR" to kt(
      """insert<Person> { set(thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }.returning { p -> Tuple(first = p.name, second = TagP("0")) }"""
    ),
    "insert/with returning params/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123}) RETURNING name, {2:myParamValue}",
      "8c4a93b2-29b6-47b3-873d-f26a1ee5d1e2" to "Joe",
      "67893fec-58ca-488d-a087-a42439868a16" to "123",
      "53c20df6-6963-4dff-8c90-2aa4b4786649" to "myParamValue"
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
      "77365f11-3377-4b4b-9a5a-a7329f30e8eb" to "1",
      "82877d78-bf6b-46d1-b8d5-a377f9a55886" to "Joe",
      "ceae10c1-ce2e-4976-bb81-4693968383a2" to "123"
    ),
    "update/with setParams and exclusion/XR" to kt(
      """update<Person> { set(thisupdate.id to TagP("0"), thisupdate.name to TagP("1"), thisupdate.age to TagP("2")).excluding(listOf(thisupdate.id)) }.filter { p -> p.id == 1 }"""
    ),
    "update/with setParams and exclusion/SQL" to cr(
      "UPDATE Person SET name = {0:Joe}, age = {1:123} WHERE id = 1",
      "283b5ff7-8cd7-44d2-8e74-bd0cc158fefd" to "Joe", "08d5728c-335a-434b-95b9-4650fe9993f2" to "123"
    ),
    "update/with returning/XR" to kt(
      "update<Person> { set(thisupdate.name to Joe, thisupdate.age to 123) }.filter { p -> p.id == 1 }.returning { p -> p.id }"
    ),
    "update/with returning/SQL" to cr(
      "UPDATE Person SET name = 'Joe', age = 123 WHERE id = 1 RETURNING id"
    ),
    "update/with returning/SQL-SqlServer" to cr(
      "UPDATE Person SET name = 'Joe', age = 123 OUTPUT INSERTED.id WHERE id = 1"
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
