package io.exoquery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.startWith

class BasicTestJvm : FunSpec() {
  init {
    test("length should return size of string") {
      "hello".length shouldBe 5
    }
    test("startsWith should test for a prefix") {
      "world" should startWith("wor")
    }
  }
}
