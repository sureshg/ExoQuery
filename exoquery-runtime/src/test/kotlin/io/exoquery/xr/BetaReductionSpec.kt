package io.exoquery.xr

import io.exoquery.xr.XR.Ident
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class BetaReductionSpec : FreeSpec({
  // TODO do properties need visible/fixed
  "simplifies the ast by applying functions" - {
    "caseclass field" {
      val ast: XR = XR.Property(XR.Product("CC", listOf(("foo" to Ident("a")))), "foo")
      BetaReduction(ast) shouldBe Ident("a")
    }
    "functionN apply" {
      val function = XR.FunctionN(listOf(Ident("a")), Ident("a"))
      val ast: XR = XR.FunctionApply(function, listOf(Ident("b")))
      BetaReduction(ast) shouldBe Ident("b")
    }
    "function1 apply" {
      val function = XR.Function1(Ident("a"), Ident("a"))
      val ast: XR = XR.FunctionApply(function, listOf(Ident("b")))
      BetaReduction(ast) shouldBe Ident("b")
    }
  }
})