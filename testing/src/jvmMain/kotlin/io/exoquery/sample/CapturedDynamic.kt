package io.exoquery.sample

import io.exoquery.sql.PostgresDialect
import io.exoquery.SqlExpression
import io.exoquery.annotation.CapturedDynamic
import io.exoquery.capture
import io.exoquery.capture.invoke
import io.exoquery.printing.pprintMisc
import io.exoquery.xr.rekeyRuntimeBinds

fun main() {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val zip: Int)
  data class Robot(val ownerId: Int, val model: String)

val q = capture.select {
  val p = from(Table<Person>())
  val a = join(Table<Address>()) { a -> p.id == a.ownerId }
  val r = join(Table<Robot>()) { r -> p.id == r.ownerId }
  Triple(p, a, r)
}
println(q.normalizeSelects().xr.show())

  //> Table(Person).flatMap { p ->
  //>   Table(Address).join { a -> p.id == a.ownerId }.flatMap {
  //>     a -> Table(Robot).join { r -> p.id == r.ownerId }.map { r ->
  //>       Triple(first = p, second = a, third = r)
  //>     }
  //>   }
  //> }


val q2 = capture {
  Table<Person>().flatMap { p ->
    internal.flatJoin(Table<Address>()) { a -> p.id == a.ownerId }.flatMap { a ->
      internal.flatJoin(Table<Robot>()) { r -> p.id == r.ownerId }.map { r ->
        Triple(p, a, r)
      }
    }
  }
}
println(q2.show())

// Table(Person).flatMap { p ->
//   Table(Address).join { a -> p.id == a.ownerId }.flatMap { a ->
//     Table(Robot).join { r -> p.id == r.ownerId }.map { r ->
//       Triple(first = p, second = a, third = r)
//     }
//   }
// }





//  fun joinedClauses() =
//    if (true) {
//      capture.expression { Table<Person>() }
//    } else {
//      capture.expression { Table<Person>() }
//    }
//
//  val filteredPeople = capture {
//    Table<Person>().filter { p -> joinedClauses().use.isNotEmpty() }
//  }

  //println("----------------- XR -----------------\n${filteredPeople.show()}")
  //println("--------------------- SQL ------------------\n" + pprintMisc(filteredPeople.build<PostgresDialect>()))
}
