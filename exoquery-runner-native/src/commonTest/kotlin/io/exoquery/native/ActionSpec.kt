package io.exoquery.native

import io.exoquery.IllegalSqlOperation
import io.exoquery.Person
import io.exoquery.SqliteDialect
import io.exoquery.capture
import io.exoquery.controller.native.NativeDatabaseController
import io.exoquery.controller.runActions
import io.exoquery.postgres.joe
import io.exoquery.postgres.people
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ActionSpec : FreeSpec({
  val ctx = TestDatabase.ctx

  beforeEach {
    ctx.runActions(
      """
      DELETE FROM Person;
      DELETE FROM Address;
      DELETE FROM Robot;
      """
    )
  }

  "insert" - {
    "simple" {
      val q = capture {
        insert<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldBe listOf(joe)
    }
    "simple with params" {
      val q = capture {
        insert<Person> { set(firstName to param("Joe"), lastName to param("Bloggs"), age to param(111)) }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldBe listOf(joe)
    }
    "simple with setParams" {
      val q = capture {
        insert<Person> { setParams(Person(1, "Joe", "Bloggs", 111)) }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldBe listOf(joe)
    }
    "simple with setParams and exclusion" {
      val q = capture {
        insert<Person> { setParams(Person(1, "Joe", "Bloggs", 111)).excluding(id) }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldBe listOf(joe)
    }
    "with returning" {
      val q = capture {
        insert<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.returning { p -> p.id + 100 }
      }
      val build = q.build<SqliteDialect>()
      build.runOn(ctx) shouldBe 101
      ctx.people() shouldBe listOf(joe)
    }
    "with returning using param" {
      val n = 1000
      val q = capture {
        insert<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.returning { p -> p.id + 100 + param(n) }
      }
      val build = q.build<SqliteDialect>()
      build.runOn(ctx) shouldBe 1101
      ctx.people() shouldBe listOf(joe)
    }
    "with returning - multiple" {
      val q = capture {
        insert<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.returning { p -> p.id to p.firstName }
      }
      val build = q.build<SqliteDialect>()
      build.runOn(ctx) shouldBe (1 to "Joe")
      ctx.people() shouldBe listOf(joe)
    }
    "with returning keys" {
      val q = capture {
        insert<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.returningKeys { id }
      }
      val build = q.build<SqliteDialect>()
      build.runOn(ctx) shouldBe 1
      ctx.people() shouldBe listOf(joe)
    }
    // Not valid because firstName is not an inserted value
    //"with returning keys multiple" {
    //  val q = capture {
    //    insert<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.returningKeys { id to firstName }
    //  }
    //  val build = q.build<SqliteDialect>()
    //  build.runOn(ctx) shouldBe (1 to "Joe")
    //  ctx.people() shouldBe listOf(joe)
    //}
  }

  suspend fun NativeDatabaseController.insertGeorgeAndJim() =
    this.runActions(
      """
        INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'George', 'Googs', 555);
        INSERT INTO Person (id, firstName, lastName, age) VALUES (2, 'Jim', 'Roogs', 222);
      """.trimIndent()
    )

  val joe = Person(1, "Joe", "Bloggs", 111)
  val george = Person(1, "George", "Googs", 555)
  val jim = Person(2, "Jim", "Roogs", 222)

  "update" - {
    "simple" {
      ctx.insertGeorgeAndJim()
      val q = capture {
        update<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.filter { p -> p.id == 1 }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldContainExactlyInAnyOrder listOf(joe, jim)
    }
    "no condition" {
      ctx.insertGeorgeAndJim()
      val q = capture {
        update<Person> { set(firstName to param("Joe"), lastName to param("Bloggs"), age to 111) }.all()
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 2
      ctx.people() shouldContainExactlyInAnyOrder listOf(
        Person(1, "Joe", "Bloggs", 111),
        Person(2, "Joe", "Bloggs", 111)
      )
    }
    "with setParams" {
      ctx.insertGeorgeAndJim()
      val updateCall = Person(1, "Joe", "Bloggs", 111)
      val q = capture {
        // TODO need to make a warning when this situation happens, can't have param instances here
        // update<Person> { setParams(Person(1, param("Joe"), param("Bloggs"), 111)) }.filter { p -> p.id == 1 }
        update<Person> { setParams(updateCall) }.filter { p -> p.id == 1 }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldContainExactlyInAnyOrder listOf(joe, jim)
    }
    "with setParams and exclusion" {
      ctx.insertGeorgeAndJim()
      // Set a large Id that should specifically be excluded from insertion
      val updateCall = Person(1000, "Joe", "Bloggs", 111)
      val q = capture {
        // Set the ID to 0 so we can be sure
        update<Person> { setParams(updateCall).excluding(id) }.filter { p -> p.id == 1 }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldContainExactlyInAnyOrder listOf(joe, jim)
    }
    "with returning" {
      ctx.insertGeorgeAndJim()
      val q = capture {
        update<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.filter { p -> p.id == 1 }.returning { p -> p.id + 100 }
      }
      val build = q.build<SqliteDialect>()
      build.runOn(ctx) shouldBe 101
      ctx.people() shouldContainExactlyInAnyOrder listOf(joe, jim)
    }
    "with returning - multiple" {
      ctx.insertGeorgeAndJim()
      val q = capture {
        update<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.filter { p -> p.id == 1 }.returning { p -> p.id to p.firstName }
      }
      val build = q.build<SqliteDialect>()
      build.runOn(ctx) shouldBe (1 to "Joe")
      ctx.people() shouldContainExactlyInAnyOrder listOf(joe, jim)
    }
    "with returningKeys" {
      val q = capture {
        update<Person> { set(firstName to "Joe", lastName to "Bloggs", age to 111) }.filter { p -> p.id == 1 }.returningKeys { id }
      }
      val build = q.build<SqliteDialect>()
      shouldThrow<IllegalSqlOperation> {
        build.runOn(ctx)
      }
    }
  }


//  "update" - {
//    "simple" {
//      val q = capture {
//        update<Person> { set(name to "Joe", age to 123) }.filter { p -> p.id == 1 }
//      }
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(q.build<SqliteDialect>(), "SQL")
//    }
//    "no condition" {
//      val q = capture {
//        update<Person> { set(name to "Joe", age to 123) }.all()
//      }
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(q.build<SqliteDialect>(), "SQL")
//    }
//    "with setParams" {
//      val q = capture {
//        update<Person> { setParams(Person(1, "Joe", 123)) }.filter { p -> p.id == 1 }
//      }.determinizeDynamics()
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(q.build<SqliteDialect>(), "SQL")
//    }
//    "with setParams and exclusion" {
//      val q = capture {
//        update<Person> { setParams(Person(1, "Joe", 123)).excluding(id) }.filter { p -> p.id == 1 }
//      }.determinizeDynamics()
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(q.build<SqliteDialect>(), "SQL")
//    }
//    "with returning" {
//      val q = capture {
//        update<Person> { set(name to "Joe", age to 123) }.filter { p -> p.id == 1 }.returning { p -> p.id }
//      }
//      val build = q.build<SqliteDialect>()
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(build, "SQL")
//      shouldBeGolden(build.actionReturningKind.toString(), "returningType")
//    }
//    "with returningKeys" {
//      val q = capture {
//        update<Person> { set(name to "Joe", age to 123) }.filter { p -> p.id == 1 }.returningKeys { id }
//      }
//      val build = q.build<SqliteDialect>()
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(build, "SQL")
//      shouldBeGolden(build.actionReturningKind.toString(), "returningType")
//    }
//  }


  "delete" - {
    "simple" {
      ctx.insertGeorgeAndJim()
      val q = capture {
        delete<Person>().filter { p -> p.id == 1 }
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 1
      ctx.people() shouldContainExactlyInAnyOrder listOf(jim)
    }
    "no condition" {
      ctx.insertGeorgeAndJim()
      val q = capture {
        delete<Person>().all()
      }
      q.build<SqliteDialect>().runOn(ctx) shouldBe 2
      ctx.people() shouldBe emptyList()
    }
    "with returning" {
      ctx.insertGeorgeAndJim()
      val q = capture {
        delete<Person>().filter { p -> p.id == 1 }.returning { p -> p.id + 100 }
      }
      val build = q.build<SqliteDialect>()
      build.runOn(ctx) shouldBe 101
      ctx.people() shouldContainExactlyInAnyOrder listOf(jim)
    }
    "with returningKeys" {
      val q = capture {
        delete<Person>().filter { p -> p.id == 1 }.returningKeys { id }
      }
      val build = q.build<SqliteDialect>()
      shouldThrow<IllegalSqlOperation> {
        build.runOn(ctx)
      }
    }
  }

//  "delete" - {
//    "simple" {
//      val q = capture {
//        delete<Person>().filter { p -> p.id == 1 }
//      }
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(q.build<SqliteDialect>(), "SQL")
//    }
//    "no condition" {
//      val q = capture {
//        delete<Person>().all()
//      }
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(q.build<SqliteDialect>(), "SQL")
//    }
//    "with returning" {
//      val q = capture {
//        delete<Person>().filter { p -> p.id == 1 }.returning { p -> p.id }
//      }
//      val build = q.build<SqliteDialect>()
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(build, "SQL")
//      shouldBeGolden(build.actionReturningKind.toString(), "returningType")
//    }
//    "with returningKeys" {
//      val q = capture {
//        delete<Person>().filter { p -> p.id == 1 }.returningKeys { id }
//      }
//      val build = q.build<SqliteDialect>()
//      shouldBeGolden(q.xr, "XR")
//      shouldBeGolden(build, "SQL")
//      shouldBeGolden(build.actionReturningKind.toString(), "returningType")
//    }
//  }


})
