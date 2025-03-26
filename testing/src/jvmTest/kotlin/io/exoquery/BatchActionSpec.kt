package io.exoquery

import io.exoquery.testdata.Person

class BatchActionSpec: GoldenSpecDynamic(GoldenQueryFile.Empty, Mode.ExoGoldenOverride(), {
  "insert" - {
    val people = listOf(Person(1, "John", 42), Person(2, "Jane", 43), Person(3, "Jack", 44),).asSequence()

    "simple" {
      val q = capture.batch(people) { p ->
        insert<Person> { set(name to param(p.name), age to param(p.age)) }
      }
      val b = q.build<PostgresDialect>().determinizeDynamics()
      val groups = b.produceBatchGroups().toList()
      shouldBeGolden(b, "SQL")
      shouldBeGolden(groups[0], "Params")
      shouldBeGolden(groups[1], "Params")
      shouldBeGolden(groups[2], "Params")
      shouldBeGoldenParams(groups)
    }

    "simple with setParams" {
      val q = capture.batch(people) { p ->
        insert<Person> { setParams(p) }
      }
      val b = q.build<PostgresDialect>().determinizeDynamics().withNonStrictEquality()
      val groups = b.produceBatchGroups().toList()
      shouldBeGolden(b, "SQL")
      shouldBeGoldenParams(groups)
    }

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
//        insert<Person> { set(name to param(p.name), age to param(p.age)) }.returning { p -> p.id to param(p.name) }
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
  }
})