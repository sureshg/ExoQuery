package io.exoquery

import io.exoquery.comapre.Compare
import io.exoquery.comapre.PrintDiff
import io.exoquery.comapre.show
import io.exoquery.util.NumbersToWords

data class Person(val id: Int, val name: String, val age: Int)
data class Address(val ownerId: Int, val street: String, val zip: Int)

// Note that "Build" -> "Rebuild" will work for this file because it is "JVM" specifically. It will not work for stuff in commonMain/Test
// in general the "Build" -> "Rebuild" only works for platform specific targets
fun main() {
//  val foofoo = { v: String -> v }
//  printSource {
//    foofoo.invoke("hello")
//  }



//  fun joes() -> List[Person]
//	  select p from Person p where p.name == 'Joe'

  val joes = capture { Table<Person>().filter { p -> p.name == "Joe" } }
  val jacks = capture { Table<Person>().filter { p -> p.name == "Jack" } }

  // then try this only with a fun and generic
  val joinPeopleToAddress2 = captureValue {
     { input: SqlQuery<Person> ->
       select {
         val p = from(input)
         // TODO now we should parse the IrGetValue. Can we found out that it is coming from captureValue? Print-debug the owernship chain
         val a = join(Table<Address>()) { a -> a.ownerId == p.id }
         p to a
       }
     }
   }

  fun joinPeopleToAddress(people: SqlQuery<Person>) =
    select {
      val p = from(people)
      val a = join(Table<Address>()) { a -> a.ownerId == p.id }
      p to a
    }

  //val result = joinPeopleToAddress2.use(jacks)
  val result = joinPeopleToAddress(jacks)

  println(result.buildPretty(PostgresDialect()).value)
}


//class Ctx {
//  fun stuff(): String = "123"
//}
//fun taker(f: Ctx.() -> String) {
//  val ctx = Ctx()
//  println(ctx.f())
//}
//
//fun stuff(): Int = 456
//
//fun blah() {
//
//  taker {
//    val x = stuff()
//    x
//  }
//}
