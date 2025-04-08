package io.exoquery.postgres

import io.exoquery.Person
import io.exoquery.PostgresDialect
import io.exoquery.TestDatabases
import io.exoquery.capture
import io.exoquery.controller.runActions
import io.kotest.core.spec.style.FreeSpec

class BatchActionSpec: FreeSpec ({
  val ctx = TestDatabases.postgres

  beforeEach {
    ctx.runActions(
      """
      TRUNCATE TABLE Person RESTART IDENTITY CASCADE;
      TRUNCATE TABLE Address RESTART IDENTITY CASCADE;
      TRUNCATE TABLE Robot RESTART IDENTITY CASCADE;
      """
    )
  }

  val people = listOf(
    Person(1, "Joe", "Bloggs", 111),
    Person(2, "Joe", "Doggs", 222),
    Person(3, "Jim", "Roogs", 333)
  ).asSequence()

  "insert" - {
    "simple" {
      val q = capture.batch(people) { p ->
        insert<Person> { set(firstName to param(p.firstName), lastName to param(p.lastName), age to param(p.age)) }
      }
      q.build<PostgresDialect>()
    }
  }
})


//  "insert" - {
//    val people = listOf(Person(1, "John", 42), Person(2, "Jane", 43), Person(3, "Jack", 44),).asSequence()
//
//    "simple" {
//      val q = capture.batch(people) { p ->
//        insert<Person> { set(name to param(p.name), age to param(p.age)) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      shouldBeGoldenParams(groups)
//    }
//
//    "simple with setParams" {
//      val q = capture.batch(people) { p ->
//        insert<Person> { setParams(p) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics().withNonStrictEquality()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//
//    "simple with setParams and exclusion" {
//      val q = capture.batch(people) { p ->
//        insert<Person> { setParams(p).excluding(id) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//
//    "with returning" {
//      val q = capture.batch(people) { p ->
//        insert<Person> { set(name to param(p.name), age to param(p.age)) }.returning { p -> p.id }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//
//    "with returning and params" {
//      val q = capture.batch(people) { p ->
//        insert<Person> { set(name to param(p.name), age to param(p.age)) }.returning { pp -> pp.id to param(p.name) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//
//    "with returning keys" {
//      val q = capture.batch(people) { p ->
//        insert<Person> { set(name to param(p.name), age to param(p.age)) }.returningKeys { id }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//  }
//  "update" - {
//    val updatedPeople = listOf(Person(1, "John-A", 52), Person(2, "Jane-A", 53), Person(3, "Jack-A", 54),).asSequence()
//
//    "simple" {
//      val q = capture.batch(updatedPeople) { p ->
//        update<Person> { set(name to param(p.name), age to param(p.age)) }.filter { pp -> pp.id == param(p.id) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGolden(groups[0], "Params1")
//      shouldBeGolden(groups[1], "Params2")
//      shouldBeGolden(groups[2], "Params3")
//      shouldBeGoldenParams(groups)
//    }
//
//    "simple - where clause" {
//      val q = capture.batch(updatedPeople) { p ->
//        update<Person> { set(name to param(p.name), age to param(p.age)) }.where { id == param(p.id) && age > 50 }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//
//    "simple with setParams and exclusion" {
//      val q = capture.batch(updatedPeople) { p ->
//        update<Person> { setParams(p).excluding(id) }.filter { pp -> pp.id == param(p.id) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//
//    "simple with setParams and exclusion and returning param" {
//      val q = capture.batch(updatedPeople) { p ->
//        update<Person> { setParams(p).excluding(id) }.filter { pp -> pp.id == param(p.id) }.returning { pp -> pp.id to param(p.name) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//  }
//
//  "delete" - {
//    "with filter - inside" {
//      val q = capture.batch(listOf(1, 2, 3).asSequence()) { id ->
//        delete<Person>().filter { it.id == param(id) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGolden(groups[0], "Params1")
//      shouldBeGolden(groups[1], "Params2")
//      shouldBeGolden(groups[2], "Params3")
//      shouldBeGoldenParams(groups)
//    }
//    "with filter" {
//      val ids = listOf(1, 2, 3).asSequence()
//      val q = capture.batch(ids) { id ->
//        delete<Person>().filter { p -> p.id == param(id) }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//    "with returning" {
//      val ids = listOf(1, 2, 3).asSequence()
//      val q = capture.batch(ids) { id ->
//        delete<Person>().filter { p -> p.id == param(id) }.returning { p -> p.id }
//      }
//      val b = q.build<PostgresDialect>().determinizeDynamics()
//      val groups = b.produceBatchGroups().toList()
//      shouldBeGolden(b, "SQL")
//      shouldBeGoldenParams(groups)
//    }
//  }
//})
