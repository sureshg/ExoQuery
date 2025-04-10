package io.exoquery.postgres

import io.exoquery.Person
import io.exoquery.PostgresDialect
import io.exoquery.capture
import io.exoquery.controller.native.DatabaseController
import io.exoquery.native.runOn

suspend fun DatabaseController.people() = capture { Table<Person>() }.build<PostgresDialect>().runOn(this)

suspend fun DatabaseController.insertPerson(person: Person) =
  capture { insert<Person> { setParams(person) } }.build<PostgresDialect>().runOn(this)

suspend fun DatabaseController.insertPeople() =
  people.forEach { capture { insert<Person> { setParams(it) } }.build<PostgresDialect>().runOn(this) }

suspend fun DatabaseController.insertAllPeople() =
  allPeople.forEach { capture { insert<Person> { setParams(it) } }.build<PostgresDialect>().runOn(this) }

val joe = Person(1, "Joe", "Bloggs", 111)
val joe2 = Person(2, "Joe", "Doggs", 222)
val jim = Person(3, "Jim", "Roogs", 333)
val george = Person(4, "George", "Googs", 444)
val people = listOf(joe, joe2, jim)
val allPeople = listOf(joe, joe2, jim, george)
