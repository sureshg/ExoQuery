package io.exoquery

import io.exoquery.testdata.Person

// TODO need  a test for exclusing
// TODO need a test for returning-keys (and that info needs to be avaiable for downstream systems i.e. the context)
class ActionReq: GoldenSpecDynamic(GoldenQueryFile.Empty, Mode.ExoGoldenOverride(), {
  "insert" - {
    "simple" {
      val q = capture {
        insert<Person> { set(name to "Joe", age to 123) }
      }
      shouldBeGolden(q.xr, "XR")
      shouldBeGolden(q.build<PostgresDialect>(), "SQL")
    }
    "simple with params" {
      val q = capture {
        insert<Person> { set(name to param("Joe"), age to param(123)) }
      }.determinizeDynamics()
      shouldBeGolden(q.xr, "XR")
      shouldBeGolden(q.build<PostgresDialect>(), "SQL")
    }
    "simple with setParams" {
      val q = capture {
        insert<Person> { setParams(Person(1, "Joe", 123)) }
      }.determinizeDynamics()
      shouldBeGolden(q.xr, "XR")
      shouldBeGolden(q.build<PostgresDialect>(), "SQL")
    }
    "with returning" {
      val q = capture {
        insert<Person> { set(name to "Joe", age to 123) }.returning { p -> p.id }
      }
      shouldBeGolden(q.xr, "XR")
      shouldBeGolden(q.build<PostgresDialect>(), "SQL")
    }
  }
})
