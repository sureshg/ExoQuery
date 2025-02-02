package io.exoquery

import io.exoquery.xr.EqualityOperator
import io.exoquery.xr.SX
import io.exoquery.xr.SelectClause
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual

class BasicSelectClauseQuotationSpec : FreeSpec({
  data class Person(val id: Int, val name: String, val age: Int)
  data class Robot(val ownerId: Int, val model: String)
  val personTpe = XRType.Product("Person", listOf("id" to XRType.Value, "name" to XRType.Value, "age" to XRType.Value))
  val robotTpe = XRType.Product("Robot", listOf("ownerId" to XRType.Value, "model" to XRType.Value))
  val personEnt = XR.Entity("Person", personTpe)
  val robotEnt = XR.Entity("Robot", robotTpe)
  val pIdent = XR.Ident("p", personTpe)
  val rIdent = XR.Ident("r", robotTpe)
  val joinRobot = XR.BinaryOp(XR.Property(pIdent, "id"), EqualityOperator.`==`, XR.Property(rIdent, "ownerId"))

  "parsing features spec" - {
    val people = capture { Table<Person>() }
    "from + join" {
      val people =
        select {
          val p = from(people)
          val r = join(Table<Robot>()) { r -> p.id == r.ownerId }
          p.name
        }

      people.xr shouldBeEqual SelectClause.of(
        from = listOf(SX.From(pIdent, personEnt)),
        joins = listOf(SX.Join(XR.JoinType.Inner, rIdent, robotEnt, rIdent, joinRobot)),
        select = XR.Property(pIdent, "name"),
        type = XRType.Value
      ).toXrRef()
    }

    "from + join + where" {
      val people =
        select {
          val p = from(people)
          val r = join(Table<Robot>()) { r -> p.id == r.ownerId }
          where(p.name == "Joe")
          p.name
        }

      people.xr shouldBeEqual SelectClause.of(
        from = listOf(SX.From(pIdent, personEnt)),
        joins = listOf(SX.Join(XR.JoinType.Inner, rIdent, robotEnt, rIdent, joinRobot)),
        where = SX.Where(XR.BinaryOp(XR.Property(pIdent, "name"), EqualityOperator.`==`, XR.Const("Joe"))),
        select = XR.Property(pIdent, "name"),
        type = XRType.Value
      ).toXrRef()
    }


  }
})
