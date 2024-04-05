package io.exoquery.norm

import io.exoquery.*
import io.exoquery.printing.exoPrint
import io.exoquery.select
import io.exoquery.sql.token
import io.exoquery.util.TraceConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class FlatQuerySpec: FreeSpec({
  val dol = '$'

  val Dialect = PostgresDialect(TraceConfig.empty)

  "query in query" {

  }

})