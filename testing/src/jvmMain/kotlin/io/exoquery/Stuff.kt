package io.exoquery

fun main() {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val zip: Int)
  val people = capture {
    Table<Person>().filter { p -> p.name == "Joe" }
  }
  val peopleAndAddresses =
    select {
      val p = from(people)
      val a = join(Table<Address>()) { a -> a.ownerId == p.id }
      p to a
    }
  peopleAndAddresses.build(PostgresDialect()) // now watch this!
}