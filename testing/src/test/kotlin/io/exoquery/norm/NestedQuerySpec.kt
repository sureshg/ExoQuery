package io.exoquery.norm

import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.exoquery.select.on
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType

/*


case class Grann(val s: String, val i: Int)
case class Parr(val s: String, val i: Int)
case class Chii(val s: String, val i: Int)
case class Bayy(val s: String, val i: Int)
case class ChaiiBayy(val chii: Chii, val bayy: Bayy)
case class GrannParr(val grann: Grann, val parr: Parr)
case class ParrChii(val parr: Parr, val chii: Chii)
case class GrannParrChii(val grann: Grann, val parr: Parr, val chii: Chii)
case class GrannParrChiiBayy(val grann: Grann, val parr: Parr, val chii: Chii, val bayy: Bayy)

val q = quote {
  for {
    grann <- query[Grann]
    parrChii <- (
       for {
        parr <- query[Parr]
        chii <- query[Chii].join(chii => chii.s == parr.s)
      } yield ParrChii(parr, chii)
    ).join(parrChii => parrChii.parr.i == grann.i)
  }  yield GrannParrChii(grann, parrChii.parr, parrChii.chii)
}

 */

data class Person(val id: Int, val name: String, val age: Int) { companion object: TableConstructor<Person> }
data class Address(val owner: Int, val street: String) { companion object: TableConstructor<Address> }


data class Grann(val s: String, val i: Int) { companion object: TableConstructor<Grann> { } }
data class Parr(val s: String, val i: Int) { companion object: TableConstructor<Parr> { } }
data class Chii(val s: String, val i: Int) { companion object: TableConstructor<Chii> { } }
data class Bayy(val s: String, val i: Int) { companion object: TableConstructor<Bayy> { } }

class NestedQuerySpec: FreeSpec({

  data class ChaiiBayy(val chii: Chii, val bayy: Bayy)
  data class GrannParr(val grann: Grann, val parr: Parr)
  data class ParrChii(val parr: Parr, val chii: Chii)
  data class GranParChii(val grann: Grann, val parr: Parr, val chii: Chii)
  data class GranParrChiiBayy(val grann: Grann, val parr: Parr, val chii: Chii, val bayy: Bayy)



  //val Dialect = PostgresDialect(TraceConfig(listOf(TraceType.Normalizations, TraceType.Standard, TraceType.SqlNormalizations)))
  val Dialect = PostgresDialect(TraceConfig.empty)

  "flat-in-flat" - {
//    "basic" {
//      val q = query {
//        val a = varFrom(A)
//        val bc = varJoin(
//          query {
//            val b = varFrom(B)
//            val c = varJoin(C).on { s == b().s }
//            select { b() to c() } // TODO see the error that happens when you just do select { b to c }
//          }
//        ).on { first.i == a().i }
//        select { a() to bc().first }
//      }
//      println(q.xr.showRaw())
//      println(Dialect.show(q.xr, true))
//    }

//    "basic - direct" {
//      val q = query {
//        val a = from(A)
//        val bc = join(
//          query {
//            val b = from(B)
//            val c = join(C).on { s == b.s }
//            select { b to c } // TODO see the error that happens when you just do select { b to c }
//          }
//        ).on { first.i == a.i }
//        select { a to bc.first }
//      }
//      println(q.xr.show())
//      println(Dialect.normalizeQuery(q.xr).show())
//      println(Dialect.show(q.xr, true))
//    }

//    "case class" { //// //// ////
//      val q = query {
//        val grann = from(Table<Grann>())
//        val parrChii = join(
//          query {
//            val parr = from(Table<Parr>())
//            val chii = join(Table<Chii>()).on { s == parr.s }
//            select { ParrChii(parr, chii) }
//          }
//        ).on { parr.i == grann.i }
//        select { GranParChii(grann, parrChii.parr, parrChii.chii) }
//      }
//      println(q.xr.showRaw())
//      println(Dialect.normalizeQuery(q.xr).showRaw())
//      println(Dialect.show(q.xr, true))
//    }

    "case class" {
      val q = query {
        val grann = from(Grann())
        val parrChii = join(
          query {
            val parr = from(Parr())
            val chii = join(Chii()).on { s == parr.s }
            select { ParrChii(parr, chii) }
          }
        ).on { parr.i == grann.i }
        select { GranParChii(grann, parrChii.parr, parrChii.chii) }
      }
      println(q.xr.showRaw())
      println(Dialect.normalizeQuery(q.xr).showRaw())
      println(Dialect.show(q.xr, true))
    }
  }
})