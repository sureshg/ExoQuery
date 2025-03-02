package io.exoquery

import io.exoquery.annotation.CapturedFunction
import io.exoquery.testdata.*

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
    "val tbl; select { from(capFun(tbl)) }" {
      val tbl = capture { Table<Person>() }
      val capJoes = capture.select { val p = from(joes(tbl)); p }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
    "cap { capFunA { capFunB } }" {
      @CapturedFunction
      fun jacks(people: SqlQuery<Person>) = capture { joes(people.filter { p -> p.name == "Jack" }) }
      val capJoes = capture { jacks(Table<Person>()) }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
    "cap { capFunA(x) -> capFunB }" {
      @CapturedFunction
      fun namedX(people: SqlQuery<Person>, name: String) = capture { joes(people.filter { p -> p.name == name }) }
      val capJoes = capture { namedX(Table<Person>(), "Jack") }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build<PostgresDialect>(), "SQL")
    }
    "cap { capFunA(x) -> capFunB(x) -> capFunC }" {
      @CapturedFunction
      fun namedX(people: SqlQuery<Person>, name: String) = capture { joes(people.filter { p -> p.name == name }) }
      @CapturedFunction
      fun namedY(people: SqlQuery<Person>, name: String) = capture { namedX(people, name) }
      val capJoes = capture { namedY(Table<Person>(), "Jack") }
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
