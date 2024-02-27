import com.github.vertical_blank.sqlformatter.SqlFormatter
import io.exoquery.*
import io.exoquery.annotation.ExoInternal
import io.exoquery.printing.format
import io.exoquery.sql.ExpandNestedQueries
import io.exoquery.sql.SqlQuery
import io.exoquery.sql.SqlQueryApply
import io.exoquery.util.Globals
import io.exoquery.util.TraceConfig
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

    var start = System.currentTimeMillis()
    val x =
      select {
        val x = from(Table<Person>())
        val a = join(Table<Address>()).on { street == x().name?.first?.name }
        x
      }
    println("------------ Query Making Time: ${(System.currentTimeMillis() - start).toDouble()/1000} ----------")

    println("=============== XR ===============")
    println(format(x.xr.show()))

    //println("=============== SQL ===============")
    start = System.currentTimeMillis()
    val sql = SqlQueryApply(Globals.traceConfig())(x.xr)
    println("------------ SqlQueryApply Time: ${(System.currentTimeMillis() - start).toDouble()/1000} ----------")
    //println(sql.showRaw())

    //println("=============== Expanded SQL ===============")
    start = System.currentTimeMillis()
    val expandedSql = ExpandNestedQueries(sql, sql.type)
    println("------------ ExpandNestedQueries Time: ${(System.currentTimeMillis() - start).toDouble()/1000} ----------")
    //println(expandedSql.showRaw())

    println("=============== Tokenized SQL ===============")
    start = System.currentTimeMillis()
    val tokenizedSql =
      with (PostgresDialect(Globals.traceConfig())) {
        expandedSql.token
      }
    println("------------ PostgresDialect Tokenization Time: ${(System.currentTimeMillis() - start).toDouble()/1000} ----------")
    println(SqlFormatter.format(tokenizedSql.toString()))

    //val reduction = BetaReduction(x.xr)
    //println("-------------- Reduction -------------\n" + format(reduction.show()))

    println(pprint(x.binds, defaultShowFieldNames = false, defaultWidth = 200))
  }
}

fun main() {
  for (i in 1..100) {
    Model1.use()
  }
}

//
//// TODO Need test name:String? because it doesn't work with XRType
//
//// TODO Use this or something like this as a test for de-aliasing
//object Model2 {
//  data class Person(val id: Int, val name: String, val age: Int)
//  data class Address(val ownerId: Int, val street: String, val zip: Int)
//
//  fun use() {
//    val p = Person(111, "Joe", 123)
//
//    // Problem of "p" vs "x". Ident needs to get the runtime value of the variable
//    // That means we need a separate binding map for runtime values or an expression-container
//    val x =
//      select {
//        val x = from(Table<Person>())
//        val a = join(Table<Address>()).on { street == x().name }
//        x
//      }
//
//    //  val x =
//    //    select {
//    //      val x = from(Table<Person>())
//    //      val a = join(Table<Address>()).on { ownerId == x().id }
//    //      x
//    //    }
//
//    println(x.xr.show(true))
//    val reduction = BetaReduction(x.xr)
//    println("-------------- Reduction -------------\n" + reduction.show(true))
//    //println(pprint(x.binds, defaultShowFieldNames = false, defaultWidth = 200))
//  }
//}


object Model3 {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val zip: Int)

  fun use() {
    val p = Person(111, "Joe", 123)

    // TODO Need to try a nested `select {  }` inside of from and `join`
    val x =
      select {
        val p = from(Table<Person>())
        val a = join(Table<Address>()).on { street == p().name }
        p
      }

    println("=============== Raw ===============\n" + x.xr.showRaw())
    println("=============== Code ===============\n" + x.xr.show(true))
  }
}



//object PrintSource1 {
//  data class Person(val firstName: String, val lastName: String, val age: Int)
//
//  fun use() {
////    println(printSource {
////      //val (x, y) = 1 to 2
////      select {
////        val x = from(Table<Person>())
////        x
////      }
////    })
//  }
//}




//@OptIn(ExoInternal::class)
//fun main() {
//  val x = 123
//  val y = 456
//
//  printSource {
//    "hello $x$y how ${123 + 456} are you $y"
//  }


  //Model3.use()



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
