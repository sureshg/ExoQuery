package io.exoquery

import io.exoquery.annotation.CapturedFunction
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.testdata.*
import io.exoquery.util.TraceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain

class CapturedFunctionReq : GoldenSpecDynamic(CapturedFunctionReqGoldenDynamic, Mode.ExoGoldenTest(), {
  @CapturedFunction
  fun joes(people: SqlQuery<Person>) = capture { people.filter { p -> p.name == "Joe" } }

  val foo: Boolean = true

  "static function capture - structural tests" - {
    "cap { capFun(Table) }" {
      val capJoes = capture { joes(Table<Person>()) }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
    "val tbl; cap { capFun(tbl) }" {
      val tbl = capture { Table<Person>() }
      val capJoes = capture { joes(tbl) }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
    "val tbl; cap { capFun(tbl).filter }" {
      val tbl = capture { Table<Person>() }
      val capJoes = capture { joes(tbl).filter { p -> p.age > 21 } }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
    "val tbl; select { from(tbl) }" {
      val tbl = capture { Table<Person>() }
      val capJoes = select { val p = from(tbl); p }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
    "val tbl; select { from(tbl); join }" {
      val tbl = capture { Table<Person>() }
      val capJoes = select { val p = from(tbl); val a = join(Table<Address>()) { a -> a.ownerId == p.id }; p to a }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
  }
  "dynamic function capture - structural tests" - {
    "val tbl(Dyn); cap { capFun(tbl) }" {
      val tbl = if (foo) capture { Table<Person>() } else capture { Table<Person>() }
      val capJoes = capture { joes(tbl) }
      val det = capJoes.determinizeDynamics()
      shouldBeGolden(tbl.xr, "XR-tbl")
      shouldBeGolden(det.xr, "XR")
      shouldBeGolden(det.runtimes.runtimes.first().second.xr, "XR->Runimtes.first.XR")
      shouldBeTrueInGolden { det.runtimes.runtimes.first().second.xr == tbl.xr }
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
  }
})
