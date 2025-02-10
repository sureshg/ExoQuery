package io.exoquery

import io.exoquery.annotation.CapturedFunction
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.testdata.*
import io.kotest.core.spec.style.FreeSpec

class CapturedFunctionSpec : FreeSpec({
  "static function capture" - {
    "proto function-capture i.e. call without capture" {
      @CapturedFunction
      fun joes(people: SqlQuery<Person>) = capture { people.filter { p -> p.name == "Joe" } }
      val people = capture { Table<Person>() }
      val drivingJoes = joes(people)

      println(people.xr.show())
    }

    println("helloo")

    //"basic function capture" {
    //  @CapturedFunction
    //  fun joes(people: SqlQuery<Person>) = capture { people.filter { p -> p.name == "Joe" } }
    //  val drivingJoes = capture { joes(Table<Person>()) }
    //  drivingJoes.xr.show()
    //}
    // captured-function with param
  }



})
