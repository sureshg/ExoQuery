package io.exoquery.postgres

import io.exoquery.testdata.PersonId
import io.exoquery.annotation.ExoEntity
import kotlinx.serialization.Serializable
import io.exoquery.testdata.Person
import io.exoquery.testdata.PersonNullable
import io.exoquery.testdata.PersonWithId
import io.exoquery.sql.PostgresDialect
import io.exoquery.TestDatabases
import io.exoquery.capture
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runActions
import io.exoquery.joe
import io.exoquery.people
import io.exoquery.jdbc.runOn
import io.exoquery.peopleNullable
import io.exoquery.peopleWithId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ColumnEncodingSpec : FreeSpec({
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

  "simple - contextual param" {
    val joeWithId = PersonWithId(PersonId(1), "Joe", "Bloggs", 123)
    val q = capture {
      insert<PersonWithId> { set(id to paramCtx(joeWithId.id), firstName to param(joeWithId.firstName), lastName to "Bloggs", age to 123) }
    }
    q.build<PostgresDialect>().runOn(ctx) shouldBe 1
    ctx.peopleWithId() shouldBe listOf(joeWithId)
  }

  "setParams - contextual param" {
    val joeWithId = PersonWithId(PersonId(1), "Joe", "Bloggs", 123)
    val q = capture {
      insert<PersonWithId> { setParams(joeWithId) }
    }
    q.build<PostgresDialect>().runOn(ctx) shouldBe 1
    ctx.peopleWithId() shouldBe listOf(joeWithId)
  }

})
