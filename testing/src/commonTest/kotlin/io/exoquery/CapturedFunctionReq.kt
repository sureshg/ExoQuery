@file:io.exoquery.annotation.TracesEnabled(TraceType.Normalizations::class, TraceType.SqlNormalizations::class)
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
  "static function capture - structural tests" - {
    @CapturedFunction
    fun joes(people: SqlQuery<Person>) = capture { people.filter { p -> p.name == "Joe" } }

    "cap { capFun(Table) }" {
      val capJoes = capture { joes(Table<Person>()) }
      shouldBeGolden(capJoes.xr, "XR")
      shouldBeGolden(capJoes.build(PostgresDialect()), "SQL")
    }

//    "val tabl; cap { capFun(tabl) }" {
//      val people = capture { Table<Person>() }
//      shouldBeGolden(joes(people).xr)
//      shouldBeGolden(joes(people).build(PostgresDialect()), "SQL")
//    }
  }



})
