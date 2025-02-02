package io.exoquery

import io.exoquery.comapre.Compare
import io.exoquery.comapre.PrintDiff
import io.exoquery.comapre.show

// Note that "Build" -> "Rebuild" will work for this file because it is "JVM" specifically. It will not work for stuff in commonMain/Test
// in general the "Build" -> "Rebuild" only works for platform specific targets
fun main() {
  //data class Person(val id: Int, val name: String, val age: Int)
  //data class Address(val ownerId: Int, val street: String, val zip: Int)
  //val people = capture {
  //  Table<Person>().filter { p -> p.name == "Joe" }
  //}
  //val peopleAndAddresses =
  //  select {
  //    val p = from(people)
  //    val a = join(Table<Address>()) { a -> a.ownerId == p.id }
  //    p to a
  //  }
  //peopleAndAddresses.build(PostgresDialect()) // now watch this!

  data class Name(val first: String, val last: String)
  data class Person(val name: Name?, val age: Int)

  val joe = Person(Name("Joe", "Smith"), 30)
  val jane = Person(null, 25)

  val diff = Compare(showSuccess = true)(joe, jane)
  println(diff.show())


}
