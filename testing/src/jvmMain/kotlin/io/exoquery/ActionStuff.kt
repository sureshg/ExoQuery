package io.exoquery

fun main() {
  data class Name(val first: String, val last: String)
  data class Person(val id: Int, val name: Name, val age: Int)

//  val output = printSourceBefore {
//    capture {
//      insert<Person> { set(name to "Joe", age to 123) }
//    }
//  }
//
//  println(output)

  val n = Name("Joe", "Smith")
  val p = Person(1, Name("joe", "smith"), 123)

  val act =
    capture {
      insert<Person> { set(name.first to param(n.first), age to 123) }.returning { p -> Name(p.name.first + "-stuff", p.name.last + "-otherStuff") }
      // TODO Odd error need to figure out: Exception in thread "main" java.lang.NoClassDefFoundError: I
      //insert<Person> { setParams(p) }.returning { p -> Name(p.name.first + "-stuff", p.name.last + "-otherStuff") }
    }

  println("--------------- XR ---------------\n${act.xr.showRaw()}")

  println("hellooooooooo")
  println(act.build<PostgresDialect>().value)

}
