package io.exoquery

import io.exoquery.select.on

class Example {
}

data class Person(val id: Int, val name: String, val age: Int)
data class Address(val owner: Int, val street: String)

fun main() {
  val q = query {
    val p = from(Table<Person>())
    val q = join(Table<Address>()).on { owner == p.id }
    select { p to q }
  }

  println(PostgresDialect().translate(q.xr))
}
