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
      // TODO need to elimiate the "p" in p.id because it does not render in the action
      //      or need to add it to the action itself
      insert<Person> { set(name to "Joe", age to 123) }.returning { p -> p.id }
    }

  println("--------------- XR ---------------\n${act.xr.showRaw()}")

  println("hellooooo")
  println(act.build<PostgresDialect>().value)

}
