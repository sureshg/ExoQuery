package io.exoquery

import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec

class BasicSelectClauseQuotationSpec : FreeSpec({
  data class Person(val id: Int, val name: String, val age: Int)
  data class Robot(val ownerId: Int, val model: String)
  val personTpe = XRType.Product("Person", listOf("id" to XRType.Value, "name" to XRType.Value, "age" to XRType.Value))
  val robotTpe = XRType.Product("Robot", listOf("ownerId" to XRType.Value, "model" to XRType.Value))
  val personEnt = XR.Entity("Person", personTpe)
  val robotEnt = XR.Entity("Robot", robotTpe)
  val pIdent = XR.Ident("p", personTpe)
  val rIdent = XR.Ident("r", robotTpe)

  // TODO first test features of the Select clause, then need to test features of the transformer to XR.Query
  // hello
  "parsing features spec" - {
    "from + join" {
      val joes = capture {
        Table<Person>().filter { p -> p.name == "Joe" }
      }
      val people =
        select {
          val p = from(joes)
          val a = join(Table<Robot>()) { a -> p.id == a.ownerId }
          p.name
        }
      println("Comppiled Query:\n${people.build(PostgresDialect())}")
    }

  }
})