@file:TracesEnabled(TraceType.SqlNormalizations::class, TraceType.Normalizations::class)

package io.exoquery

import io.exoquery.annotation.CapturedDynamic
import io.exoquery.annotation.CapturedFunction
import io.exoquery.annotation.TracesEnabled
import io.exoquery.comapre.Compare
import io.exoquery.comapre.PrintDiff
import io.exoquery.comapre.show
import io.exoquery.util.NumbersToWords
import io.exoquery.util.TraceType
import io.exoquery.xr.BetaReduction

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

  // TODO beta reduce out ExprOfQuery(QueryOfExpr()), also probably want to check for that in the SqlQuery builder

  val joes = capture { Table<Person>().filter { p -> p.name == "Joe" } }
  val jacks = capture { Table<Person>().filter { p -> p.name == "Jack" } }


  // Need tests for both the static and dynamic variations of this
  // need to test where both the people variable as well as the joinPeopleToAddress is dynamic (the latter might be a bit trickier e.g. it needs to itself call a dynamic function
  @CapturedFunction
  fun joinPeopleToAddress(people: SqlQuery<Person>) =
    select {
      val p = from(people)
      val a = join(Table<Address>()) { a -> a.ownerId == p.id }
      p to a
    }

// can test how this thing is actually parsed
//  val result = joinPeopleToAddress(jacks)
//  println(result.show())
//  println("hello")

  val result = capture {
    /*
    ======= Could not parse expression from: =======
    <destruct>.component1()
    --------- With the Tree ---------
    [IrCall] Fun(component1) - dispatch=kotlin.Pair
      [IrGetValue] Param(<destruct>)
     */
    //joinPeopleToAddress(jacks).map { (p, a) -> p.name + " lives at " + a.street }
    joinPeopleToAddress(joes).map { kv -> kv.first.name + " lives at " + kv.second.street }
  }
  println(result.show())

  println(result.build<PostgresDialect>().value)

  println("helloooooooooooooo")

  // then try this only with a fun and generic
//  val joinPeopleToAddress2 = captureValue {
//     { input: SqlQuery<Person> ->
//       select {
//         val p = from(input)
//         // TODO now we should parse the IrGetValue. Can we found out that it is coming from captureValue? Print-debug the owernship chain
//         val a = join(Table<Address>()) { a -> a.ownerId == p.id }
//         p to a
//       }
//     }
//   }
//  println("hello")
//
//  val x = capture {
//    joinPeopleToAddress2.use.invoke(joes)
//  }
//  println(x.buildPretty(PostgresDialect()).value) // helloo
//
//
//  println("hello")
//
//  println("---------------------\n" + x.xr.show())
//  println("------------------ Reduced: ------------------\n" + BetaReduction(x.xr))


//  fun joinPeopleToAddress(people: @CapturedDynamic SqlQuery<Person>) =
//    select {
//      val p = from(people)
//      val a = join(Table<Address>()) { a -> a.ownerId == p.id }
//      p to a
//    }
//
//  //val result = joinPeopleToAddress2.use(jacks)
//  val result = joinPeopleToAddress(jacks)
//  println(result.buildPretty(PostgresDialect()).value)
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
