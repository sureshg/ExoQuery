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

  val x = printSource {
    select {
      val k = join(TableQuery<Address>()).on { street == "someplace" }
      k
    }
  }

  println(x)

//  val v =
//    select {
//      val x = from(TableQuery<Person>()).withName("foo")
//      x
//    }
//
//  println(pprint(v, defaultShowFieldNames = false, defaultWidth = 200))
}