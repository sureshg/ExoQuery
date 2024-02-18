package io.exoquery.util

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class InterpolatorSpec :  FreeSpec({
  val interp = Tracer(TraceType.Standard, TraceConfig.empty, defaultIndent = 0, color = false, globalTracesEnabled = { true })

  data class Small(val id: Int)
  val small = Small(123)

  "traces small objects on single line - single" {
    interp("small object: $small").generateString() shouldBe ("small object: Small(123) " to 0)
  }


  data class Large(
    val id: Int,
    val one: String,
    val two: String,
    val three: String,
    val four: String,
    val five: String,
    val six: String,
    val seven: String,
    val eight: String,
    val nine: String,
    val ten: String
  )
  var vars = (0 until 10).map { i -> (0 until i).map { _ -> "Test" }.joinToString("") }.toList()
  val large = Large(123, vars[0], vars[1], vars[2], vars[3], vars[4], vars[5], vars[6], vars[7], vars[8], vars[9])

  "traces large objects on multiple line - single" {
    interp("large object: $large").generateString() shouldBe ((
      """large object:
        ||  Large(
        ||    123,
        ||    "",
        ||    "Test",
        ||    "TestTest",
        ||    "TestTestTest",
        ||    "TestTestTestTest",
        ||    "TestTestTestTestTest",
        ||    "TestTestTestTestTestTest",
        ||    "TestTestTestTestTestTestTest",
        ||    "TestTestTestTestTestTestTestTest",
        ||    "TestTestTestTestTestTestTestTestTest"
        ||  )
        |""".trimMargin() to 0
      ))
  }

})