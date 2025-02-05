package io.exoquery

import io.exoquery.annotation.Captured
import io.exoquery.comapre.Compare
import io.exoquery.comapre.PrintDiff
import io.exoquery.comapre.show
import io.exoquery.util.NumbersToWords

data class Person(val id: Int, val name: String, val age: Int)
data class Address(val ownerId: Int, val street: String, val zip: Int)

// Note that "Build" -> "Rebuild" will work for this file because it is "JVM" specifically. It will not work for stuff in commonMain/Test
// in general the "Build" -> "Rebuild" only works for platform specific targets
fun main() {
//  fun joes() -> List[Person]
//	  select p from Person p where p.name == 'Joe'

  val joes = capture { Table<Person>().filter { p -> p.name == "Joe" } }
  val jacks = capture { Table<Person>().filter { p -> p.name == "Jack" } }

  fun joinPeopleToAddress(people: @Captured SqlQuery<Person>) =
    select {
      val p = from(people)
      val a = join(Table<Address>()) { a -> a.ownerId == p.id }
      p to a
    }

  val result = joinPeopleToAddress(jacks)

  println(result.buildPretty(PostgresDialect()).value)
}
