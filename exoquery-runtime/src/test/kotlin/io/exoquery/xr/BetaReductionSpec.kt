package io.exoquery.xr

import io.exoquery.tupleOf
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class BetaReductionSpec : FreeSpec({
  // TODO do properties need visible/fixed
  "simplifies the ast by applying functions" - {
    "caseclass field" {
      val ast: XR = Property(Product("CC", listOf(("foo" to Ident("a")))), "foo")
      BetaReduction(ast) shouldBe Ident("a")
    }
    "functionN apply" {
      val function = FunctionN(listOf(Ident("a")), Ident("a"))
      val ast: XR = FunctionApply(function, listOf(Ident("b")))
      BetaReduction(ast) shouldBe Ident("b")
    }
    "function1 apply" {
      val function = Function1(Ident("a"), Ident("a"))
      val ast: XR = FunctionApply(function, listOf(Ident("b")))
      BetaReduction(ast) shouldBe Ident("b")
    }
  }
  "replaces identifiers by actuals" - {
    "ident" {
      val ast: XR = Ident("a")
      BetaReduction(ast, Ident("a") to Ident("a'")) shouldBe
        Ident("a'")
    }
  }
  "with inline" - {
    val entity = Entity("a")
    val root = Ident("root")
    // Entity Idents
    val (aE, bE, cE, dE) = tupleOf(Ident("a"), Ident("b"), Ident("c"), Ident("d"))
    // Value Idents
    val (a, b, c, d) = tupleOf(Ident("a"), Ident("b"), Ident("c"), Ident("d"))
    val (c1, c2, c3) = tupleOf(Const.Int(1), Const.Int(2), Const.Int(3))

    "top level block" {
      val block = Block(
        listOf(
          Variable(aE, root),
          Variable(bE, aE)
        ),
        Property(bE, "foo")
      )
      BetaReduction.invoke(block) shouldBe Property(root, "foo")
    }
  }

  /*
    "with inline" - {
      val entity = Entity("a", Nil, QEP)
      // Entity Idents
      val (aE, bE, cE, dE) = tupleOf(Ident("a", QEP), Ident("b", QEP), Ident("c", QEP), Ident("d", QEP))
      // Value Idents
      val (a, b, c, d) = tupleOf(Ident("a", QV), Ident("b", QV), Ident("c", QV), Ident("d", QV))
      val (c1, c2, c3) = tupleOf(Constant.auto(1), Constant.auto(2), Constant.auto(3))

      "top level block" in {
        val block = Block(
          List(
            Val(aE, entity),
            Val(bE, aE),
            Map(bE, dE, c1)
          )
        )
        BetaReduction.AllowEmpty(block) mustEqual Map(entity, dE, c1)
      }
      "nested blocks" in {
        val inner = Block(
          List(
            Val(aE, entity),
            Val(b, c2),
            Val(c, c3),
            Tuple(List(aE, bE, cE))
          )
        )
        val outer = Block(
          List(
            Val(aE, inner),
            Val(bE, aE),
            Val(cE, bE),
            cE
          )
        )
        BetaReduction.AllowEmpty(outer) mustEqual Tuple(List(entity, c2, c3))
      }
      "nested blocks caseclass" in {
        val inner = Block(
          List(
            Val(aE, entity),
            Val(b, c2),
            Val(c, c3),
            CaseClass("CC", List(("foo", aE), ("bar", bE), ("baz", cE)))
          )
        )
        val outer = Block(
          List(
            Val(aE, inner),
            Val(bE, aE),
            Val(cE, bE),
            cE
          )
        )
        BetaReduction.AllowEmpty(outer) mustEqual CaseClass("CC", List(("foo", entity), ("bar", c2), ("baz", c3)))
      }
    }

   */
})