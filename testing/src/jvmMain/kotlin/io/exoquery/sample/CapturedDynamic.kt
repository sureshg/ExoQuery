package io.exoquery.sample

import io.exoquery.sql.PostgresDialect
import io.exoquery.SqlExpression
import io.exoquery.annotation.CapturedDynamic
import io.exoquery.capture
import io.exoquery.capture.invoke
import io.exoquery.printing.pprintMisc
import io.exoquery.xr.rekeyRuntimeBinds

fun main() {
  data class Person(val name: String, val age: Int)

  val possibleNames = listOf("Joe", "Jack")

  @CapturedDynamic
  fun joinedClauses(p: SqlExpression<Person>) =
    possibleNames.map { n -> capture.expression { p.use.name == param(n) } }.reduce { a, b -> capture.expression { a.use || b.use } }

  val filteredPeople = capture {
    Table<Person>().filter { p -> joinedClauses(capture.expression { p }).use }
  }

  println("----------------- XR -----------------\n${filteredPeople.show()}")
  println("--------------------- SQL ------------------\n" + pprintMisc(filteredPeople.build<PostgresDialect>()))
}
