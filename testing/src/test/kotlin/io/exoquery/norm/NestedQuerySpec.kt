package io.exoquery.norm

import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.exoquery.util.TraceConfig

class NestedQuerySpec: FreeSpec({
  data class AT(val s: String, val i: Int, val b: Boolean)
  data class BT(val s: String, val i: Int, val b: Boolean)
  data class CT(val s: String, val i: Int, val b: Boolean)
  data class DT(val s: String, val i: Int, val b: Boolean)

  val A = Table<AT>()
  val B = Table<BT>()
  val C = Table<CT>()
  val D = Table<DT>()

  data class CD(val ct: CT, val dt: DT)
  data class AB(val at: AT, val bt: BT)
  data class BC(val bt: BT, val ct: CT)
  data class ABC(val at: AT, val bt: BT, val ct: CT)
  data class ABCD(val at: AT, val bt: BT, val ct: CT, val dt: DT)

  val Dialect = PostgresDialect(TraceConfig.empty)

  "flat-in-flat" - {
    "basic" {
      val q = query {
        val a = fromDirect(A)
        val bc = join(
          query {
            val b = fromDirect(B)
            val c = join(C).onDirect { s == b.s }
            //select { BC(b, c) }
            select { b to c }
          }
        ).onDirect { first.i == a.i }
        //).onDirect { bt.i == a.i }
        //select { ABC(a, bc.bt, bc.ct) }
        select { a to bc.first }
      }
      println(q.xr.show())
      println(Dialect.show(q.xr))
    }
  }
})