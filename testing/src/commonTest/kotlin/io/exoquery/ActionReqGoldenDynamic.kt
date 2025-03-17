package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object ActionReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "insert/simple/XR" to kt(
      "Table(Person).insert { set(thisinsert.name to Joe, thisinsert.age to 123) }"
    ),
    "insert/simple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123)"
    ),
    "insert/simple with params/XR" to kt(
      """Table(Person).insert { set(thisinsert.name to TagP("0"), thisinsert.age to TagP("1")) }"""
    ),
    "insert/simple with params/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123})",
      "0" to "Joe", "1" to "123"
    ),
    "insert/simple with setParams/XR" to kt(
      """Table(Person).insert { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }"""
    ),
    "insert/simple with setParams/SQL" to cr(
      "INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123})",
      "0" to "1", "1" to "Joe", "2" to "123"
    ),
    "insert/simple with setParams and exclusion/XR" to kt(
      """Table(Person).insert { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id)) }"""
    ),
    "insert/simple with setParams and exclusion/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123})",
      "1" to "Joe", "2" to "123"
    ),
    "insert/simple with setParams and exclusion - multiple/XR" to kt(
      """Table(Person).insert { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id, thisinsert.name)) }"""
    ),
    "insert/simple with setParams and exclusion - multiple/SQL" to cr(
      "INSERT INTO Person (age) VALUES ({0:123})",
      "2" to "123"
    ),
    "insert/with returning/XR" to kt(
      "Table(Person).insert { set(thisinsert.name to Joe, thisinsert.age to 123) }.returning { p -> p.id }"
    ),
    "insert/with returning/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) RETURNING id"
    ),
    "insert/with returning/returningType" to cr(
      "ClauseInQuery"
    ),
    "insert/with returning - multiple/XR" to kt(
      "Table(Person).insert { set(thisinsert.name to Joe, thisinsert.age to 123) }.returning { p -> Tuple(first = p.id, second = p.name) }"
    ),
    "insert/with returning - multiple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) RETURNING id, name"
    ),
    "insert/with returning - multiple/returningType" to cr(
      "ClauseInQuery"
    ),
    "insert/with returningKeys/XR" to kt(
      "Table(Person).insert { set(thisinsert.name to Joe, thisinsert.age to 123) }.returningKeys { listOf(thisreturningKeys.id) }"
    ),
    "insert/with returningKeys/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123)"
    ),
    "insert/with returningKeys/returningType" to cr(
      "Explicit(columns=[id])"
    ),
    "insert/with returningKeys - multiple/XR" to kt(
      "Table(Person).insert { set(thisinsert.name to Joe, thisinsert.age to 123) }.returningKeys { listOf(thisreturningKeys.id, thisreturningKeys.name) }"
    ),
    "insert/with returningKeys - multiple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123)"
    ),
    "insert/with returningKeys - multiple/returningType" to cr(
      "Explicit(columns=[id, name])"
    ),
  )
}
