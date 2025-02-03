@file:io.exoquery.annotation.ExoGoldenTest

package io.exoquery

import io.exoquery.Ord.*
import io.exoquery.xr.EqualityOperator
import io.exoquery.xr.SX
import io.exoquery.xr.SelectClause
import io.exoquery.xr.XR
import io.exoquery.xr.XR.Ordering.TupleOrdering
import io.exoquery.xr.XR.Product.Companion.TupleSmartN
import io.exoquery.xr.XR.Expression
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual

class AtomicValueSelectSpec : GoldenSpec(BasicSelectClauseQuotationSpecGolden, {
  data class Person(val id: Int, val name: String, val age: Int)
  data class Robot(val ownerId: Int, val model: String)
  data class Address(val ownerId: Int, val street: String, val zip: Int)
  val personTpe = XRType.Product("Person", listOf("id" to XRType.Value, "name" to XRType.Value, "age" to XRType.Value))
  val robotTpe = XRType.Product("Robot", listOf("ownerId" to XRType.Value, "model" to XRType.Value))
  val addressTpe = XRType.Product("Address", listOf("ownerId" to XRType.Value, "street" to XRType.Value, "zip" to XRType.Value))
  val personEnt = XR.Entity("Person", personTpe)
  val robotEnt = XR.Entity("Robot", robotTpe)
  val addressEnt = XR.Entity("Address", addressTpe)
  val pIdent = XR.Ident("p", personTpe)
  val rIdent = XR.Ident("r", robotTpe)
  val aIdent = XR.Ident("a", addressTpe)
  val joinRobot = XR.BinaryOp(XR.Property(pIdent, "id"), EqualityOperator.`==`, XR.Property(rIdent, "ownerId"))
  val joinAddress = XR.BinaryOp(XR.Property(pIdent, "id"), EqualityOperator.`==`, XR.Property(aIdent, "ownerId"))

  infix fun Expression.prop(propName: String): Expression = XR.Property(this, propName)

  "parsing features spec" - {

    // apply-map takes care of the non-nested case
    "from(atom) + join -> (p, r)" {
      val names = capture { Table<Person>().map { p -> p.name } /*.nested()*/ }
      val people =
        select {
          val n = from(names)
          val r = join(Table<Robot>()) { r -> n == r.model }
          n to r
        }

      println(people.buildPretty(PostgresDialect(), "from + join -> (p, r)").value)
    }

    // TODO 2-table select with atomic value
    // TODO distinct on atomic-value
  }
})
