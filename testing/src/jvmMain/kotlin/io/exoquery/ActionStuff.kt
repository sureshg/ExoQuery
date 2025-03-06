package io.exoquery

fun main() {
  data class Person(val id: Int, val name: String, val age: Int)

  println("hello")
  printSourceBefore {
    capture {
      insert<Person> { set(name to "Joe", age to 123) }
    }
  }
}
