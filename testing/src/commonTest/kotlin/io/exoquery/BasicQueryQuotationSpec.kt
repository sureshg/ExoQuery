package io.exoquery

import io.exoquery.xr.OP
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual

class BasicQueryQuotationSpec : FreeSpec({
  data class Person(val id: Int, val name: String, val age: Int)
  data class Robot(val ownerId: Int, val model: String)
  val personTpe = XRType.Product("Person", listOf("id" to XRType.Value, "name" to XRType.Value, "age" to XRType.Value))
  val robotTpe = XRType.Product("Robot", listOf("ownerId" to XRType.Value, "model" to XRType.Value))
  val personEnt = XR.Entity("Person", personTpe)
  val robotEnt = XR.Entity("Robot", robotTpe)
  val pIdent = XR.Ident("p", personTpe)
  val rIdent = XR.Ident("r", robotTpe)

  "map compile" {
    val cap0 = capture { Table<Person>() }
    val cap = capture { cap0.map { p -> p.name } }

    val dialect = PostgresDialect()
    println("---------------- Sql ----------------\n"+dialect.translate(cap.xr))
  }


  // Testing of the query DSL note that the quotation mechanics are mostly testing in BasicExpressionQuotationSpec,
  // here we are largely testing the DSL mechanics
  "dsl features" - {
    "table" {
      val cap = capture { Table<Person>() }
      cap.determinizeDynamics() shouldBeEqual SqlQuery(
        personEnt,
        Runtimes.Empty,
        Params.Empty
      )
      // Some XR check omit the types so want to explicitly check this just in case
      cap.xr.type shouldBeEqual personTpe
    }
    "map" {
      val cap0 = capture { Table<Person>() }
      val cap = capture { cap0.map { p -> p.name } }
      cap.determinizeDynamics() shouldBeEqual SqlQuery(
        XR.Map(personEnt, pIdent, XR.Property(pIdent, "name")),
        Runtimes.Empty,
        Params.Empty
      )
    }
    "flatMap" {
      val cap0 = capture { Table<Robot>() }
      val cap = capture { cap0.flatMap { r -> Table<Person>() } }
      cap.determinizeDynamics() shouldBeEqual SqlQuery(
        XR.FlatMap(robotEnt, rIdent, personEnt),
        Runtimes.Empty,
        Params.Empty
      )
    }
    "filter" {
      val cap0 = capture { Table<Person>() }
      val cap = capture { cap0.filter { p -> p.age > 18 } }
      cap.determinizeDynamics() shouldBeEqual SqlQuery(
        XR.Filter(personEnt, pIdent, XR.BinaryOp(XR.Property(pIdent, "age"), OP.gt, XR.Const.Int(18))),
        Runtimes.Empty,
        Params.Empty
      )
    }
    // TODO need to build special logic for converting name.toList into "unnest(name)" and probably need to have a custom parseable unnest in the DSL or something like that
    //"concatMap" {
    //  val cap0 = capture { Table<Person>() }
    //  val cap = capture { cap0.concatMap { p -> p.name.toList() } }
    //  cap.determinizeDynamics() shouldBeEqual SqlQuery(
    //    XR.ConcatMap(personEnt, pIdent, ???),
    //    Runtimes.Empty,
    //    Params.Empty
    //  )
    //}
    "union and unionAll" - {
      val cap0 = capture { Table<Person>() }
      val capA = capture { cap0.filter { p -> p.name == "A" } }
      val capB = capture { cap0.filter { p -> p.name == "B" } }

      "union" {
        val cap = capture { capA.union(capB) }
        cap.determinizeDynamics() shouldBeEqual SqlQuery(
          XR.Union(
            XR.Filter(personEnt, pIdent, XR.BinaryOp(XR.Property(pIdent, "name"), OP.`==`, XR.Const.String("A"))),
            XR.Filter(personEnt, pIdent, XR.BinaryOp(XR.Property(pIdent, "name"), OP.`==`, XR.Const.String("B")))
          ),
          Runtimes.Empty,
          Params.Empty
        )
      }
      "unionAll" {
        val cap = capture { capA.unionAll(capB) }
        cap.determinizeDynamics() shouldBeEqual SqlQuery(
          XR.UnionAll(
            XR.Filter(personEnt, pIdent, XR.BinaryOp(XR.Property(pIdent, "name"), OP.`==`, XR.Const.String("A"))),
            XR.Filter(personEnt, pIdent, XR.BinaryOp(XR.Property(pIdent, "name"), OP.`==`, XR.Const.String("B")))
          ),
          Runtimes.Empty,
          Params.Empty
        )
      }
    }
  }
  "dynamic quotation mechanics" - {
    val tru = true
    "c0D=dyn{TableA}, c={c0D.map(...)} -> {T(B0).map(...),R={B0,TableA}}" {
      val cap0 =
        if (tru) {
          capture { Table<Person>() }
        } else {
          throw IllegalArgumentException("Should not be here")
        }
      val cap = capture {
        cap0.map { p -> p.name }
      }
      cap.determinizeDynamics() shouldBeEqual SqlQuery(
        XR.Map(XR.TagForSqlQuery(BID("0"), personTpe), pIdent, XR.Property(pIdent, "name")),
        Runtimes.of(BID("0") to SqlQuery<Person>(personEnt, Runtimes.Empty, Params.Empty)),
        Params.Empty
      )
    }
  }
})
