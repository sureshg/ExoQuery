package io.exoquery

import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kotlin.test.assertEquals

//class GoldenSpec(build: FreeSpec.() -> kotlin.Unit): FreeSpec(build) {
//  override suspend fun beforeSpec(spec: Spec) {
//    super.beforeSpec(spec)
//
//  }
//}

class SelectClauseQuerySpec : FreeSpec({

  "nested datatypes" - {
    data class Person(val name: String, val age: Int)

    "simple join" {
      val people = capture {
        Table<Person>().filter { p -> p.name == "Joe" }
      }

      people.build<PostgresDialect>().value shouldBe "SELECT p.name AS name, p.age AS age FROM Person p WHERE p.name = 'Joe'"
    }


    //data class Name(val first: String, val last: String)
    //data class Person(val name: Name?, val age: Int)
    //data class Robot(val ownerFirstName: String, val model: String)


//    "from + join on nullable" {
//
//      val people =
//        select {
//          val p = from(Table<Person>())
//          val r = join(Table<Robot>()) { r -> p.name?.first == r.ownerFirstName }
//          p to r
//        }
//      println("Query:\n${people.build<PostgresDialect>()}")
//    }
//    "from + join on nullable ?: alternative" {
//      val people =
//        select {
//          val p = from(Table<Person>())
//          val r = join(Table<Robot>()) { r -> p.name?.first ?: "defaultName" == r.ownerFirstName }
//          p to r
//        }
//      println("Query:\n${people.build<PostgresDialect>()}") //
//    }

  }
})
