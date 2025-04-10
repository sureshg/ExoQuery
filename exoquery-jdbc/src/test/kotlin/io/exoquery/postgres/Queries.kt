package io.exoquery.postgres

import io.exoquery.Person
import io.exoquery.PostgresDialect
import io.exoquery.capture
import io.exoquery.controller.Controller
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.runOn
import io.exoquery.sql.SqlIdiom

suspend fun JdbcController.people() = capture { Table<Person>() }.build<PostgresDialect>().runOn(this)

suspend fun JdbcController.insertPerson(person: Person) =
  capture { insert<Person> { setParams(person) } }

suspend fun JdbcController.insertPeople() =
  people.forEach { capture { insert<Person> { setParams(it) } }.build<PostgresDialect>().runOn(this) }

suspend fun JdbcController.insertAllPeople() =
  allPeople.forEach { capture { insert<Person> { setParams(it) } }.build<PostgresDialect>().runOn(this) }

val joe = Person(1, "Joe", "Bloggs", 111)
val joe2 = Person(2, "Joe", "Doggs", 222)
val jim = Person(3, "Jim", "Roogs", 333)
val george = Person(4, "George", "Googs", 444)
val people = listOf(joe, joe2, jim)
val allPeople = listOf(joe, joe2, jim, george)
