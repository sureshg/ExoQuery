package io.exoquery

import io.exoquery.annotation.CapturedDynamic
import io.exoquery.annotation.CapturedFunction
import io.exoquery.printing.pprintMisc
import io.exoquery.testdata.Person

class CapturedFunctionDynamicReq : GoldenSpecDynamic(CapturedFunctionReqGoldenDynamic, Mode.ExoGoldenOverride(), {
  "dynamic catpured function with join clauses" {
    val possibleNames = listOf("Joe", "Jack", "Jill")

    @CapturedDynamic
    fun joinedClauses(p: SqlExpression<Person>) =
      possibleNames.map { n -> capture.expression { p.use.name == param(n) } }.reduce { a, b -> capture.expression { a.use || b.use } }

    val filteredPeople = capture {
      Table<Person>().filter { p -> joinedClauses(capture.expression { p }).use }
    }

    shouldBeGolden(filteredPeople.xr, "XR")
    shouldBeGolden(filteredPeople.build<PostgresDialect>(), "SQL")
  }
})
