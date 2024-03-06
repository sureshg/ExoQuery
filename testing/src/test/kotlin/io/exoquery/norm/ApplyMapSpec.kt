package io.exoquery.norm

import io.kotest.core.spec.style.FreeSpec
import io.exoquery.*
import io.exoquery.util.TraceConfig
import io.kotest.matchers.shouldBe

class ApplyMapSpec: FreeSpec({
  val ApplyMap = ApplyMap(TraceConfig.empty)

  "applies intermediate map" - {
    "flatMap" {
      val q = qr1.map { y -> y.s }.flatMap { s -> qr2.filter { z -> z.s == s } }
      val n = qr1.flatMap { y -> qr2.filter { z -> z.s == y.s } }
      ApplyMap(q.xr) shouldBe n.xr
    }
  }

})