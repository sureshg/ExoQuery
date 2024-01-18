import io.exoquery.*
import io.exoquery.annotation.ExoInternal
import io.exoquery.printing.format
import io.exoquery.xr.BetaReduction

object Model1 {
  data class Person(val id: Int, val name: Name?, val age: Int)
  data class Address(val ownerId: Int, val street: String, val zip: Int)
  data class Name(val first: First?)
  data class First(val name: String)

  fun use() {
    val p = Person(111, Name(First("Joe")), 123)

    // Problem of "p" vs "x". Ident needs to get the runtime value of the variable
    // That means we need a separate binding map for runtime values or an expression-container
    val x =
      select {
        val x = from(Table<Person>())
        val a = join(Table<Address>()).on { street == x().name?.first?.name }
        x
      }

    println(format(x.xr.show()))

    val reduction = BetaReduction(x.xr)
    println("-------------- Reduction -------------\n" + format(reduction.show()))

    println(pprint(x.binds, defaultShowFieldNames = false, defaultWidth = 200))
  }
}

// TODO Need test name:String? because it doesn't work with XRType

object Model2 {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val zip: Int)

  fun use() {
    val p = Person(111, "Joe", 123)

    // Problem of "p" vs "x". Ident needs to get the runtime value of the variable
    // That means we need a separate binding map for runtime values or an expression-container
    val x =
      select {
        val x = from(Table<Person>())
        val a = join(Table<Address>()).on { street == x().name }
        x
      }

    println(x.xr.show(true))
    val reduction = BetaReduction(x.xr)
    println("-------------- Reduction -------------\n" + reduction.show(true))
    //println(pprint(x.binds, defaultShowFieldNames = false, defaultWidth = 200))
  }
}

@OptIn(ExoInternal::class)
fun main() {


  Model2.use()

  // TODO simple test of aliasing i.e. it should be x2
//  val x =
//    select {
//      val x = from(Table<Person>())
//      val a = join(Table<Address>()).on { ownerId == x().id }
//      x
//    }

  //println(pprint(x, defaultShowFieldNames = false, defaultWidth = 200))


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