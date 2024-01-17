package io.exoquery.xr

import io.exoquery.tupleOf
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

fun XR.Product.Companion.TupleN(elements: List<XR.Expression>) =
  when(elements.size) {
    0 -> XR.Product("Empty", listOf())
    1 -> XR.Product("Single", listOf("first" to elements[0]))
    2 -> XR.Product("Pair", listOf("first" to elements[0], "second" to elements[1]))
    3 -> XR.Product("Triple", listOf("first" to elements[0], "second" to elements[1], "third" to elements[2]))
    4 -> XR.Product("Tuple4", listOf("first" to elements[0], "second" to elements[1], "third" to elements[2], "fourth" to elements[3]))
    5 -> XR.Product("Tuple5", listOf("first" to elements[0], "second" to elements[1], "third" to elements[2], "fourth" to elements[3], "fifth" to elements[4]))
    6 -> XR.Product("Tuple6", listOf("first" to elements[0], "second" to elements[1], "third" to elements[2], "fourth" to elements[3], "fifth" to elements[4], "sixth" to elements[5]))
    else -> throw IllegalArgumentException("Only up to 6 elements are supported for this operation")
  }


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

    "nested blocks" {
      val inner = Block(
        listOf(
          Variable(aE, root),
          Variable(b, c2),
          Variable(c, c3)
        ),
        XR.Product.TupleN(listOf(aE, bE, cE))
      )
      val outer = Block(
        listOf(
          Variable(aE, inner),
          Variable(bE, aE),
          Variable(cE, bE)
        ),
        cE
      )
      BetaReduction.invoke(outer).show() shouldBe XR.Product.TupleN(listOf(root, c2, c3)).show()
    }

    "nested blocks product" {
      val inner = Block(
        listOf(
          Variable(aE, root),
          Variable(b, c2),
          Variable(c, c3)
        ),
        XR.Product("CC", listOf("foo" to aE, "bar" to bE, "baz" to cE))
      )
      val outer = Block(
        listOf(
          Variable(aE, inner),
          Variable(bE, aE),
          Variable(cE, bE)
        ),
        cE
      )
      BetaReduction.invoke(outer) shouldBe XR.Product("CC", listOf("foo" to root, "bar" to c2, "baz" to c3))
    }
  }



})