package io.exoquery.postgres

import io.exoquery.Person
import io.exoquery.PostgresDialect
import io.exoquery.TestDatabases
import io.exoquery.capture
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runActions
import io.exoquery.runOn
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

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

  "insert" - {

    "simple" {
      ctx.insertPerson(george)
      val q = capture.batch(people.asSequence()) { p ->
        insert<Person> { set(firstName to param(p.firstName), lastName to param(p.lastName), age to param(p.age)) }
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(1, 1, 1)
      ctx.people() shouldContainExactlyInAnyOrder allPeople
    }

    "simple with setParams" {
      ctx.insertPerson(george)
      val q = capture.batch(people.asSequence()) { p ->
        insert<Person> { setParams(p) }
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(1, 1, 1)
      ctx.people() shouldContainExactlyInAnyOrder allPeople
    }

    "simple with setParams and exclusion" {
      ctx.insertPerson(george)
      // Modify the ids to make sure it is inserting records with a new Id, not the ones used here
      val insertPeople = people.map { it.copy(id = it.id + 100) }.asSequence()
      val q = capture.batch(insertPeople) { p ->
        insert<Person> { setParams(p).excluding(id) }
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(1, 1, 1)
      ctx.people() shouldContainExactlyInAnyOrder allPeople
    }

    "with returning" {
      ctx.insertPerson(george)
      val q = capture.batch(people.asSequence()) { p ->
        insert<Person> { set(firstName to param(p.firstName), lastName to param(p.lastName), age to param(p.age)) }.returning { p -> p.id + 100 }
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(101, 102, 103)
      ctx.people() shouldContainExactlyInAnyOrder allPeople
    }

    "with returning and params" {
      ctx.insertPerson(george)
      val q = capture.batch(people.asSequence()) { p ->
        insert<Person> { set(firstName to param(p.firstName), lastName to param(p.lastName), age to param(p.age)) }.returning { pp -> pp.id + 100 to param(p.firstName) }
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(101, 102, 103)
      ctx.people() shouldContainExactlyInAnyOrder allPeople
    }
    "with returning keys" {
      ctx.insertPerson(george)
      val q = capture.batch(people.asSequence()) { p ->
        insert<Person> { set(firstName to param(p.firstName), lastName to param(p.lastName), age to param(p.age)) }.returningKeys { id }
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(1, 2, 3)
      ctx.people() shouldContainExactlyInAnyOrder allPeople
    }
  }

  "update" - {
    val updatedPeople = listOf(Person(1, "Joe-A", "Bloggs-A", 112), Person(2, "Joe-A", "Doggs-A", 222), Person(3, "Jim-A", "Roogs-A", 333))
    val allNewPeople = (updatedPeople + george).asSequence()
    ctx.insertAllPeople()

  }
})


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
