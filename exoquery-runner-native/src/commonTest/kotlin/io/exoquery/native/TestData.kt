package io.exoquery.postgres

import io.exoquery.testdata.Person
import io.exoquery.SqliteDialect
import io.exoquery.capture
import io.exoquery.controller.native.NativeDatabaseController
import io.exoquery.native.runOn

suspend fun NativeDatabaseController.people() = capture { Table<Person>() }.build<SqliteDialect>().runOn(this)

suspend fun NativeDatabaseController.insertPerson(person: Person) =
  capture { insert<Person> { setParams(person) } }.build<SqliteDialect>().runOn(this)

suspend fun NativeDatabaseController.insertPeople() =
  people.forEach { capture { insert<Person> { setParams(it) } }.build<SqliteDialect>().runOn(this) }

suspend fun NativeDatabaseController.insertAllPeople() =
  allPeople.forEach { capture { insert<Person> { setParams(it) } }.build<SqliteDialect>().runOn(this) }

val joe = Person(1, "Joe", "Bloggs", 111)
val joe2 = Person(2, "Joe", "Doggs", 222)
val jim = Person(3, "Jim", "Roogs", 333)
val george = Person(4, "George", "Googs", 444)
val people = listOf(joe, joe2, jim)
val allPeople = listOf(joe, joe2, jim, george)
