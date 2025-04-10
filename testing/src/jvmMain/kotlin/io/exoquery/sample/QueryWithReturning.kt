package io.exoquery.sample

import io.exoquery.*

fun main() {
  data class Person(val name: String, val age: Int)

  val myParam = "foo"
  val q = capture {
    insert<Person> { set(name to param("Joe"), age to param(123)) }.returning { p -> p.name to param(myParam) }
  }
  val b = q.build<PostgresDialect>().determinizeDynamics()

  println(b.params)

  println(b.token.showRaw())
}
