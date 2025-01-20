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

  "parsing features spec" - {
    "from" {
      val people =
        select {
          val p = from(Table<Person>())
          p.name
        }
      println(people.show())
    }
  }
})