package io.exoquery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.startWith

// Note that "Build" -> "Rebuild" will work for this file because it is "JVM" specifically. It will not work for stuff in commonMain/Test
// in general the "Build" -> "Rebuild" only works for platform specific targets
class BasicTestJvm : FunSpec() {
  init {
    test("length should return size of string") {
      "hello".length shouldBe 5
    }
    test("startsWith should test for a prefix") {
      "world" should startWith("wor") //hello
    }
  }
}
