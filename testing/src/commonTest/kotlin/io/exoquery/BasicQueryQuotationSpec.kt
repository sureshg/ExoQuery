package io.exoquery

import io.exoquery.xr.`+++`
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual

class BasicQueryQuotationSpec : FreeSpec({
  data class Person(val name: String, val age: Int)
  val personTpe = XRType.Product("Person", listOf("name" to XRType.Value, "age" to XRType.Value))
  val personEnt = XR.Entity("Person", personTpe)
  val pIdent = XR.Ident("p", personTpe)

  "static cases" - {
    "table" {
      val cap = capture { Table<Person>() }
      cap.determinizeDynamics() shouldBeEqual SqlQuery(
        personEnt,
        Runtimes.Empty,
        Params.of()
      )
      // Some XR check omit the types so want to explicitly check this just in case
      cap.xr.type shouldBeEqual personTpe
    }
    "map" {
      val cap0 = capture { Table<Person>() }
      val cap = capture { cap0.map { p -> p.name } }
      println(cap.show())

      //cap.determinizeDynamics() shouldBeEqual SqlQuery(
      //  XR.Map(personEnt, pIdent, XR.Property(pIdent, "name")),
      //  Runtimes.Empty,
      //  Params.of()
      //)
    }
  }
  "dynamic cases" - {
    val tru = true
    "c0D=dyn{TableA}, c={c0D.map(...)} -> {T(B0).map(...),R={B0,TableA}}" {
      val cap0 =
        if (tru) {
          capture { Table<Person>() }
        } else {
          throw IllegalArgumentException("Should not be here")
        }
      val cap = capture { cap0.map { p -> p.name } }
      println(cap.show())
    }
  }
})
