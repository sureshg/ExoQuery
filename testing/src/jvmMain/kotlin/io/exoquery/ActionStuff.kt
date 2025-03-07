package io.exoquery

fun main() {
  data class Person(val id: Int, val name: String, val age: Int)

//  val output = printSourceBefore {
//    capture {
//      insert<Person> { set(name to "Joe", age to 123) }
//    }
//  }
//
//  println(output)

  val act =
    capture {
      insert<Person> { set(name to "Joe", age to 123) }
    }

  println("--------------- XR ---------------\n${act.xr.showRaw()}")

  println("hellooooo")
  println(act.build<PostgresDialect>().value)

}
