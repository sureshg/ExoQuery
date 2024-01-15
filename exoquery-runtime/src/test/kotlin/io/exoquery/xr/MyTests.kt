package io.exoquery.xr

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class MyTests : FreeSpec({
  "String.length" - {
    "should return the length of the string" {
      "sammy".length shouldBe 5
      "".length shouldBe 0
    }
  }
  "containers can be nested as deep as you want" - {
    "and so we nest another container" - {
      "yet another container" - {
        "finally a real test" {
          1 + 1 shouldBe 2
        }
      }
    }
  }
})



