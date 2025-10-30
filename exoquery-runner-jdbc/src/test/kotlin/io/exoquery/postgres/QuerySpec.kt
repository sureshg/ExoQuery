package io.exoquery.postgres

import io.exoquery.testdata.Address
import io.exoquery.Ord
import io.exoquery.testdata.Person
import io.exoquery.PostgresDialect
import io.exoquery.testdata.Robot
import io.exoquery.TestDatabases
import io.exoquery.sql
import io.exoquery.controller.runActions
import io.exoquery.jdbc.runOn
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import kotlinx.serialization.Serializable
import kotlin.to


class QuerySpec : FreeSpec({
  val ctx = TestDatabases.postgres

  beforeSpec {
    ctx.runActions(
      """
      DELETE FROM Person;
      DELETE FROM Address;
      DELETE FROM Robot;
      INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'Joe', 'Bloggs', 111);
      INSERT INTO Person (id, firstName, lastName, age) VALUES (2, 'Joe', 'Doggs', 222);
      INSERT INTO Person (id, firstName, lastName, age) VALUES (3, 'Jim', 'Roogs', 333);
      INSERT INTO Address (ownerId, street, zip) VALUES (1, '123 Main St', '12345');
      INSERT INTO Address (ownerId, street, zip) VALUES (1, '456 Elm St', '67890');
      INSERT INTO Address (ownerId, street, zip) VALUES (2, '789 Oak St', '54321');
      INSERT INTO Robot (ownerId, model, age) VALUES (2, 'R2D2', 22);
      INSERT INTO Robot (ownerId, model, age) VALUES (3, 'C3PO', 33);
      INSERT INTO Robot (ownerId, model, age) VALUES (3, 'T100', 44);
      """
    )
  }

  "simple" {
    val q = sql { Table<Person>() }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222),
      Person(3, "Jim", "Roogs", 333)
    )
  }

  "filter" {
    val q = sql { Table<Person>().filter { it.firstName == "Joe" } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222)
    )
  }

  "filter with param" {
    val joe = "Joe"
    val q = sql { Table<Person>().filter { it.firstName == param(joe) } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222)
    )
  }

  "filter with params list" {
    val lastNames = listOf("Bloggs", "Doggs")
    val q = sql { Table<Person>().filter { it.lastName in params(lastNames) } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222)
    )
  }

  "where" {
    val q = sql { Table<Person>().where { firstName == "Joe" } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222)
    )
  }

  "filter + sortedBy" {
    val q = sql { Table<Person>().filter { it.firstName == "Joe" }.sortedBy { it.age } }
    q.build<PostgresDialect>().runOn(ctx) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222)
    )
  }

  "map with aggregations" {
    val q = sql { Table<Person>().map { p -> Triple(min(p.age), max(p.age), avg(p.age)) } }
    val query = q.build<PostgresDialect>()
    val result = query.runOn(ctx)
    result.size shouldBe 1
    result[0].first shouldBe 111
    result[0].second shouldBe 333
    result[0].third.toInt() shouldBe 222
  }

  "co-related + valueOf" {
    val q = sql { Table<Person>().filter { p -> p.age > Table<Person>().map { p -> avg(p.age) }.value() } }
    val query = q.build<PostgresDialect>()
    val result = query.runOn(ctx)
    result shouldContainExactlyInAnyOrder listOf(
      Person(3, "Jim", "Roogs", 333)
    )
  }

  "filter + correlated isNotEmpty" {
    val q = sql { Table<Person>().filter { p -> Table<Address>().filter { it.ownerId == p.id }.isNotEmpty() } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222)
    )
  }

  "filter + correlated isEmpty" {
    val q = sql { Table<Person>().filter { p -> Table<Address>().filter { it.ownerId == p.id }.isEmpty() } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(3, "Jim", "Roogs", 333)
    )
  }

  "sort + take" {
    val q = sql { Table<Person>().sortedBy { it.age }.take(1) }
    q.build<PostgresDialect>().runOn(ctx) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111)
    )
  }

  "sort + drop" {
    val q = sql { Table<Person>().sortedBy { it.age }.drop(1) }
    q.build<PostgresDialect>().runOn(ctx) shouldBe listOf(
      Person(2, "Joe", "Doggs", 222),
      Person(3, "Jim", "Roogs", 333)
    )
  }

  "distinct" {
    val q = sql { Table<Person>().map { it.firstName }.distinct() }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      "Joe",
      "Jim"
    )
  }

  "distinctOn" {
    val q = sql { Table<Person>().distinctOn { it.firstName } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(3, "Jim", "Roogs", 333)
    )
  }

  "sort + drop + take" {
    val q = sql { Table<Person>().sortedBy { it.age }.drop(1).take(1) }
    q.build<PostgresDialect>().runOn(ctx) shouldBe listOf(
      Person(2, "Joe", "Doggs", 222)
    )
  }

  "map" {
    val q = sql { Table<Person>().map { it.firstName to it.lastName } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      "Joe" to "Bloggs",
      "Joe" to "Doggs",
      "Jim" to "Roogs"
    )
  }

  @Serializable
  data class Name(val first: String, val last: String)

  @Serializable
  data class CustomPerson(val name: Name, val age: Int)

  "map to custom" {
    val q = sql { Table<Person>().map { CustomPerson(Name(it.firstName, it.lastName), it.age) } }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      CustomPerson(Name("Joe", "Bloggs"), 111),
      CustomPerson(Name("Joe", "Doggs"), 222),
      CustomPerson(Name("Jim", "Roogs"), 333)
    )
  }

  "nested" {
    val q = sql { Table<Person>().nested() }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222),
      Person(3, "Jim", "Roogs", 333)
    )
  }

  "union" {
    val bloggs = sql { Table<Person>().filter { it.lastName == "Bloggs" } }
    val doggs = sql { Table<Person>().filter { it.lastName == "Doggs" } }
    val q = sql { bloggs union doggs }
    q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Joe", "Doggs", 222)
    )
  }


//  "joins" - {
//    @Serializable
//    data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)
//    @Serializable
//    data class Address(val ownerId: Int, val street: String, val zip: String)
//
//    "SELECT Person, Address - join" {
//      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address>>().runOn(ctx) shouldBe listOf(
//        Person(1, "Joe", "Bloggs", 111) to Address(1, "123 Main St", "12345")
//      )
//    }
//
//    "SELECT Person, Address - leftJoin + null" {
//      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address?>>().runOn(ctx) shouldBe listOf(
//        Person(1, "Joe", "Bloggs", 111) to Address(1, "123 Main St", "12345"),
//        Person(2, "Jim", "Roogs", 222) to null
//      )
//    }
//

  // TODO probably only need to test for one DB e.g. postgres, move it out
  "deconstruct" - {
    "columns - from a table" {
      val names = sql.select {
        val p = from(Table<Person>())
        p.firstName to p.lastName
      }
      val q = sql.select {
        val (first, last) = from(names)
        first + " - " + last
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
        "Joe - Bloggs",
        "Joe - Doggs",
        "Jim - Roogs"
      )
    }

    "columns - from a table - mapped" {
      val names = sql.select {
        val p = from(Table<Person>())
        p.firstName to p.lastName
      }
      val q = sql {
        names.map { (first, last) -> first + " - " + last }
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
        "Joe - Bloggs",
        "Joe - Doggs",
        "Jim - Roogs"
      )
    }

    "join - from a join" {
      val join =
        sql.select {
          val p = from(Table<Person>())
          val a = join(Table<Address>()) { a -> a.ownerId == p.id }
          p to a
        }
      val deconstuct =
        sql.select {
          val (p, a) = from(join)
          val r = join(Table<Robot>()) { r -> r.ownerId == p.id }
          Triple(p, a, r)
        }

      deconstuct.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
        Triple(
          Person(2, "Joe", "Doggs", 222),
          Address(2, "789 Oak St", "54321"),
          Robot(2, "R2D2", 22)
        )
      )
    }
  }

  "joins" - {
    "Person, Address - join" {
      val q = sql.select {
        val p = from(Table<Person>())
        val a = join(Table<Address>()) { a -> a.ownerId == p.id }
        p to a
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
        Person(1, "Joe", "Bloggs", 111) to Address(1, "123 Main St", "12345"),
        Person(1, "Joe", "Bloggs", 111) to Address(1, "456 Elm St", "67890"),
        Person(2, "Joe", "Doggs", 222) to Address(2, "789 Oak St", "54321")
      )
    }

    "Person, Address - left join" {
      val q = sql.select {
        val p = from(Table<Person>())
        val a = joinLeft(Table<Address>()) { a -> a.ownerId == p.id }
        p to a
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
        Person(1, "Joe", "Bloggs", 111) to Address(1, "123 Main St", "12345"),
        Person(1, "Joe", "Bloggs", 111) to Address(1, "456 Elm St", "67890"),
        Person(2, "Joe", "Doggs", 222) to Address(2, "789 Oak St", "54321"),
        Person(3, "Jim", "Roogs", 333) to null
      )
    }

    "Person, Address - left-join + groupBy(name)" {
      val q = sql.select {
        val p = from(Table<Person>())
        val a = joinLeft(Table<Address>()) { a -> a.ownerId == p.id }
        groupBy(p.firstName)
        Triple(p.firstName, sum(p.age), count(a?.street))
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
        Triple("Joe", 444, 3),
        Triple("Jim", 333, 0)
      )
    }

    "Person, Address - left-join + groupBy(name) + filter" {
      val q = sql.select {
        val p = from(Table<Person>())
        val a = joinLeft(Table<Address>()) { a -> a.ownerId == p.id }
        where { p.lastName == "Doggs" || p.lastName == "Roogs" }
        groupBy(p.firstName)
        Triple(p.firstName, sum(p.age), count(a?.street))
      }
      q.build<PostgresDialect>().runOn(ctx) shouldContainExactlyInAnyOrder listOf(
        Triple("Joe", 222, 1),
        Triple("Jim", 333, 0)
      )
    }

    "Person, address - left-join + groupBy(name) + filter + orderBy" {
      val q = sql.select {
        val p = from(Table<Person>())
        val a = joinLeft(Table<Address>()) { a -> a.ownerId == p.id }
        where { p.lastName == "Doggs" || p.lastName == "Roogs" }
        groupBy(p.firstName, p.age)
        sortBy(p.age to Ord.Desc)
        Triple(p.firstName, p.age, count(a?.street))
      }
      q.build<PostgresDialect>().runOn(ctx) shouldBe listOf(
        Triple("Jim", 333, 0),
        Triple("Joe", 222, 1)
      )
    }
  }
})
