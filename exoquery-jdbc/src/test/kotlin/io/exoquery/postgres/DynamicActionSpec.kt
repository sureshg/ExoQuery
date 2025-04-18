package io.exoquery.postgres

import io.exoquery.Person
import io.exoquery.sql.PostgresDialect
import io.exoquery.SqlExpression
import io.exoquery.TestDatabases
import io.exoquery.annotation.CapturedDynamic
import io.exoquery.capture
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runActions
import io.exoquery.george
import io.exoquery.insertAllPeople
import io.exoquery.people
import io.exoquery.jdbc.runOn
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class DynamicActionSpec: FreeSpec({
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

  "should be able to construct a dynamic clause using parameterization" {
    val expectedPeople = people.map { p -> p.copy(firstName = p.firstName + "-A") } + george
    val namesToModify = people.map { it.lastName }
    @CapturedDynamic
    fun conditionClause(p: SqlExpression<Person>) =
      namesToModify
        .map { n -> capture.expression { p.use.lastName == param(n) } }
        .reduce { a, b -> capture.expression { a.use || b.use } }

    ctx.insertAllPeople()
    val update =
      capture {
        update<Person> {
          set(firstName to firstName + "-A")
        }.filter { p ->
          conditionClause(capture.expression { p }).use
        }
      }

    val compiledAction = update.build<PostgresDialect>()
    compiledAction.value shouldBe "UPDATE Person SET firstName = (firstName || '-A') WHERE lastName = ? OR lastName = ? OR lastName = ?"
    compiledAction.runOn(ctx) shouldBe 3L
    ctx.people() shouldContainExactlyInAnyOrder expectedPeople
  }
})
