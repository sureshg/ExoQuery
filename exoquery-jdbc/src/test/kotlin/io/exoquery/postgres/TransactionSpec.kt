package io.exoquery.postgres

import io.exoquery.Person
import io.exoquery.PostgresDialect
import io.exoquery.TestDatabases
import io.exoquery.capture
import io.exoquery.controller.runActions
import io.exoquery.controller.transaction
import io.exoquery.printSource
import io.exoquery.runOn
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class TransactionSpec : FreeSpec({
  val ctx = TestDatabases.postgres

  beforeEach {
    ctx.runActions(
      """
      DELETE FROM Person;
      DELETE FROM Address;
      """
    )
  }

  // Moving these values out here causes an explosion. Need to look into it.
  // Also need to see what happens when similar things are done for param(...)
  //val joe = Person(1, "Joe", "Bloggs", 111)
  //val jack = Person(2, "Jack", "Roogs", 222)


  val joe = Person(1, "Joe", "Bloggs", 111)
  val jack = Person(2, "Jack", "Roogs", 222)


  suspend fun select() =
    capture { Table<Person>() }.build<PostgresDialect>().runOn(ctx)

  "transaction support" - {
    "success" {
      ctx.transaction {
        capture {
          insert<Person> { setParams(joe) }
        }.build<PostgresDialect>().runOnTransaction()
      }
      select() shouldBe listOf(joe)
    }

    "failure" {
      capture {
        insert<Person> { setParams(joe) }
      }.build<PostgresDialect>().runOn(ctx)

      val cap = capture {
        insert<Person> { setParams(jack) }
      }.build<PostgresDialect>()

      shouldThrow<IllegalStateException> {
        ctx.transaction {
          cap.runOnTransaction()
          throw IllegalStateException()
        }
      }
      select() shouldBe listOf(joe)
    }

    "nested" {
      val cap = capture {
        insert<Person> { setParams(joe) }
      }.build<PostgresDialect>()

      ctx.transaction {
        ctx.transaction {
          cap.runOnTransaction()
        }
      }
      select() shouldBe listOf(joe)
    }
  }
})


//class TransactionSpecOps<Session, Stmt, ExecutionOpts>(
//  val ctx: ControllerTransactional<Session, Stmt, ExecutionOpts>,
//) {
//
//  fun clearTables(): Unit = runBlocking {
//    ctx.runActions(
//      """
//      DELETE FROM Person;
//      DELETE FROM Address;
//      """
//    )
//  }
//
//  fun success() = runBlocking {
//    ctx.transaction {
//      insert(joe).run()
//    }
//    select().runOn(ctx) shouldBe listOf(joe)
//  }
//
//  fun failure() = runBlocking {
//    insert(joe).runOn(ctx)
//    shouldThrow<IllegalStateException> {
//      ctx.transaction {
//        insert(jack).run()
//        throw IllegalStateException()
//      }
//    }
//    select().runOn(ctx) shouldBe listOf(joe)
//  }
//
//  fun nested() = runBlocking {
//    ctx.transaction {
//      ctx.transaction {
//        insert(joe).run()
//      }
//    }
//    select().runOn(ctx) shouldBe listOf(joe)
//  }
//}
