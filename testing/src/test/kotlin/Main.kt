import io.exoquery.*
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.annotation.ExoInternal

data class Person(val id: Int, val name: Name, val age: Int) {
  fun foo() = 123
  val bar = 456
}

data class Address(val ownerId: Int, val street: String, val zip: Int)

data class Name(val first: String)
//data class Person2(val nameeeeee: Name?, val age: Int)


@OptIn(ExoInternal::class)
fun main() {
  val p = Person(111, Name("Joe"), 123)

  // Problem of "p" vs "x". Ident needs to get the runtime value of the variable
  // That means we need a separate binding map for runtime values or an expression-container
  val x =
    select {
      val p = from(TableQuery<Person>())
      val k = join(TableQuery<Address>()).on { street == p().name.first }
      p
    }

  //println(pprint(x, defaultShowFieldNames = false, defaultWidth = 200))
  println(x.xr.show())
  println(pprint(x.binds, defaultShowFieldNames = false, defaultWidth = 200))

//  println(
//    printSource {
//      when {
//        p.age > 100 -> "old"
//        else -> "young"
//      }
//    }
//  )

//  val v =
//    select {
//      val x = from(TableQuery<Person>()).withName("foo")
//      x
//    }
//
//  println(pprint(v, defaultShowFieldNames = false, defaultWidth = 200))
}