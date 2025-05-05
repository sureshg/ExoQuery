package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object ActionOnConflictReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "onConflictUpdate/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictUpdate(listOf(thishidden.id))(listOf(thishidden.name to excluding.name, thishidden.age to excluding.age))"
    ),
    "onConflictUpdate/SQL" to cr(
      "INSERT INTO Person AS x (name, age) VALUES ('Joe', 123) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age"
    ),
    "onConflictUpdate - complex/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictUpdate(listOf(thishidden.id))(listOf(thishidden.name to x.name + excluding.name, thishidden.age to x.age + excluding.age))"
    ),
    "onConflictUpdate - complex/SQL" to cr(
      "INSERT INTO Person AS x (name, age) VALUES ('Joe', 123) ON CONFLICT (id) DO UPDATE SET name = (x.name || EXCLUDED.name), age = (x.age + EXCLUDED.age)"
    ),
    "onConflictUpdate - complex + returning/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictUpdate(listOf(thishidden.id))(listOf(thishidden.name to x.name + excluding.name, thishidden.age to x.age + excluding.age)).returning { p -> p.id }"
    ),
    "onConflictUpdate - complex + returning/SQL" to cr(
      "INSERT INTO Person AS x (name, age) VALUES ('Joe', 123) ON CONFLICT (id) DO UPDATE SET name = (x.name || EXCLUDED.name), age = (x.age + EXCLUDED.age) RETURNING id"
    ),
    "onConflictUpdate - multiple/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictUpdate(listOf(thishidden.id, thishidden.name))(listOf(thishidden.name to excluding.name, thishidden.age to excluding.age))"
    ),
    "onConflictUpdate - multiple/SQL" to cr(
      "INSERT INTO Person AS x (name, age) VALUES ('Joe', 123) ON CONFLICT (id, name) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age"
    ),
    "onConflictUpdate - setParams/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }.onConflictUpdate(listOf(thishidden.id))(listOf(thishidden.name to excluding.name, thishidden.age to excluding.age))"""
    ),
    "onConflictUpdate - setParams/SQL" to cr(
      "INSERT INTO Person AS x (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age",
      "4063c98a-ec90-4b49-b5d5-2d5e3bd547fe" to "1", "97e755f0-15c7-4842-acd4-369b6f22f59e" to "Joe", "eeb350ca-f279-4e66-949f-3f843902e39d" to "123"
    ),
    "onConflictUpdate - setParams + exclusion/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id)) }.onConflictUpdate(listOf(thishidden.id))(listOf(thishidden.name to excluding.name, thishidden.age to excluding.age))"""
    ),
    "onConflictUpdate - setParams + exclusion/SQL" to cr(
      "INSERT INTO Person AS x (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age",
      "72aab2d7-65fa-49ea-98af-69ffb0028a38" to "1", "ef0f84af-e1f6-4945-8c9a-d718336b8104" to "Joe", "7356e569-78ea-4294-b9e7-e57ec83ffdf9" to "123"
    ),
    "onConflictIgnore/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictIgnore(listOf(thisinsert.id))"
    ),
    "onConflictIgnore/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) ON CONFLICT (id) DO NOTHING"
    ),
    "onConflictIgnore - multiple/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictIgnore(listOf(thisinsert.id, thisinsert.name))"
    ),
    "onConflictIgnore - multiple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ('Joe', 123) ON CONFLICT (id, name) DO NOTHING"
    ),
    "onConflictIgnore - multiple - setParams/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")) }.onConflictIgnore(listOf(thisinsert.id, thisinsert.name))"""
    ),
    "onConflictIgnore - multiple - setParams/SQL" to cr(
      "INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) ON CONFLICT (id, name) DO NOTHING",
      "bc129ad0-48b2-443f-9f77-bb3315e8893b" to "1", "8af0bdfc-ac63-447a-8db1-f406d808c5a5" to "Joe", "c1b1db54-1f24-4981-890e-9ec7219033d0" to "123"
    ),
    "onConflictIgnore - setParams + exclusion/XR" to kt(
      """insert<Person> { set(thisinsert.id to TagP("0"), thisinsert.name to TagP("1"), thisinsert.age to TagP("2")).excluding(listOf(thisinsert.id)) }.onConflictIgnore(listOf(thisinsert.id, thisinsert.name))"""
    ),
    "onConflictIgnore - setParams + exclusion/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Joe}, {1:123}) ON CONFLICT (id, name) DO NOTHING",
      "394c468b-e093-4f89-b6ab-d1065982b633" to "Joe", "067a0ed2-8999-47cb-966a-44df7f535254" to "123"
    ),
  )
}
