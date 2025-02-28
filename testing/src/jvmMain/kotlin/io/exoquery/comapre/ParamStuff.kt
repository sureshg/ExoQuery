package io.exoquery.comapre

import io.exoquery.Person
import io.exoquery.capture

fun main() {
  val n = "Joe"
  fun stuff() = "joe"
  val q = capture {
    Table<Person>().filter { p -> p.name == param(stuff()) }
  }
}
