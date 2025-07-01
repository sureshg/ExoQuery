package io.exoquery

import io.exoquery.testdata.Address
import io.exoquery.testdata.Person

class QueryValuePositionReq: GoldenSpecDynamic(QueryValuePositionReqGoldenDynamic, Mode.ExoGoldenTest(), {
  "counting query + from + toValue" {
    val people =
      capture.select {
        val p = from(Table<Person>())
        Pair(
          p.name,
          capture.select {
            val a = from(Table<Address>())
            where { a.street == "123 St." }
            count(a.ownerId)
          }.value()
        )
      }.dyanmic()
    shouldBeGolden(people.xr, "XR")
    shouldBeGolden(people.buildFor.Postgres())
  }
  "select a constant" {
    val one = capture.select { 1 }
    shouldBeGolden(one.xr, "XR")
    shouldBeGolden(one.buildFor.Postgres())
  }
})
