import io.exoquery.EntityExpression
import io.exoquery.TableQuery
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.pprint
import io.exoquery.annotation.ExoInternal
import io.exoquery.select

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
  val t = true
  //val p2 = Person2(Name("Joe"), 123)//
  val ent = EntityExpression(XR.Entity("foo", XRType.Product("foo", listOf())))

  fun takeFun(f: (String) -> Int) = f("foo")

  val v =
    select {
      val x = from(TableQuery<Person>()).withName("foo")
      x
    }

//    TableQuery<Person>().flatMap { p ->
//      TableQuery<Address>().map { a -> a().ownerId != p().id }
//    }
  println(pprint(v, defaultShowFieldNames = false, defaultWidth = 200))
}