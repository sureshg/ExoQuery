package io.exoquery.sample

import io.exoquery.SqlQuery
import io.exoquery.annotation.CapturedFunction
import io.exoquery.capture
import io.exoquery.sql.PostgresDialect

interface Nameable {
  val name: String
}

fun main() {

  data class Person(override val name: String, val age: Int) : Nameable
  data class Robot(override val name: String, val model: String, val factoryId: Int) : Nameable
  data class Factory(val id: Int, val name: String)

  @CapturedFunction
  fun <N : Nameable> nameIsJoe(input: SqlQuery<N>) =
    capture {
      input.filter { p -> p.name == "Joe" }.map { p -> p.name }
    }

  // TODO add this case to the unit tests
//  val q = capture {
//    nameIsJoe(
//      Table<Robot>().filter { r ->
//        Table<Factory>().filter { f -> f.id == r.factoryId }.filter { f -> f.name == "aaa" }.isNotEmpty()
//      }
//      unionAll
//      Table<Robot>().filter { r ->
//        Table<Factory>().filter { f -> f.id == r.factoryId }.filter { f -> f.name == "bbb" }.isNotEmpty()
//      }
//    )
//  }

  @CapturedFunction
  fun robotsWithFactoryName(factoryName: String) =
    capture {
      Table<Robot>().filter { r ->
        Table<Factory>().filter { f -> f.id == r.factoryId }.filter { f -> f.name == factoryName }.isNotEmpty()
      }
    }

  val q = capture {
    nameIsJoe(
      robotsWithFactoryName("aaa") unionAll robotsWithFactoryName("bbb")
    )
  }

  /*
  SELECT FROM nameIsJoe(SELECT * FROM Robots where model = 'R2D2')
   */

  println(q.buildPretty<PostgresDialect>().value)
}
