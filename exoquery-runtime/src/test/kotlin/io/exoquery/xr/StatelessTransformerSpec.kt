package io.exoquery.xr

import io.kotest.core.spec.style.FreeSpec
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.isSubclassOf

class StatelessTransformerSpec : FreeSpec({
  class Subject(vararg val replace: Pair<XR, XR>): StatelessTransformerSingleRoot {
    override fun <X : XR> root(xr: X): X {
      val rep = replace.toMap().getOrElse(xr, { xr })
      return when {
        !rep::class.isSubclassOf(xr::class) ->
          throw IllegalArgumentException("Cannot replace ${xr}:${xr::class.simpleName} with ${rep}:${rep::class.simpleName} since they have different types")
        else -> rep as X
      }
    }
  }

  "transforms asts" - {
    "query" - {
      "filter" {
        val ast: XR = Filter(Entity("a"), Ident("b"), Ident("c"))
        Subject(Entity("a") to Entity("a'"), Ident("b") to Ident("b'"), Ident("c") to Ident("c'"))(ast) shouldBe
          Filter(Entity("a'"), Ident("b"), Ident("c'"))
      }
      "map" {
        val ast: XR = Map(Entity("a"), Ident("b"), Ident("c"))
        Subject(Entity("a") to Entity("a'"), Ident("b") to Ident("b'"), Ident("c") to Ident("c'"))(ast) shouldBe
          Map(Entity("a'"), Ident("b"), Ident("c'"))
      }
      "flatMap" {
        val ast: XR = FlatMap(Entity("a"), Ident("b"), Entity("c"))
        Subject(Entity("a") to Entity("a'"), Ident("b") to Ident("b'"), Entity("c") to Entity("c'"))(ast) shouldBe
          FlatMap(Entity("a'"), Ident("b"), Entity("c'"))
      }
      "concatMap" {
        val ast: XR = ConcatMap(Entity("a"), Ident("b"), Ident("c"))
        Subject(Entity("a") to Entity("a'"), Ident("b") to Ident("b'"), Ident("c") to Ident("c'"))(ast) shouldBe
          ConcatMap(Entity("a'"), Ident("b"), Ident("c'"))
      }
      "sortBy" {
        val ast: XR = SortBy(Entity("a"), Ident("b"), Ident("c"), Ordering.AscNullsFirst)
        Subject(Entity("a") to Entity("a'"), Ident("b") to Ident("b'"), Ident("c") to Ident("c'"))(ast) shouldBe
          SortBy(Entity("a'"), Ident("b"), Ident("c'"), Ordering.AscNullsFirst)
      }
      "aggregation" {
        val ast: XR = Aggregation(AggregationOperator.`max`, Ident("a"))
        Subject(Ident("a") to Ident("a'"))(ast) shouldBe
          Aggregation(AggregationOperator.`max`, Ident("a'"))
      }
      "take" {
        val ast: XR = Take(Entity("a"), Ident("b"))
        Subject(Entity("a") to Entity("a'"), Ident("b") to Ident("b'"))(ast) shouldBe
          Take(Entity("a'"), Ident("b'"))
      }
      "drop" {
        val ast: XR = Drop(Entity("a"), Ident("b"))
        Subject(Entity("a") to Entity("a'"), Ident("b") to Ident("b'"))(ast) shouldBe
          Drop(Entity("a'"), Ident("b'"))
      }
      "union" {
        val ast: XR = Union(Entity("a"), Entity("b"))
        Subject(Entity("a") to Entity("a'"), Entity("b") to Entity("b'"))(ast) shouldBe
          Union(Entity("a'"), Entity("b'"))
      }
      "unionAll" {
        val ast: XR = UnionAll(Entity("a"), Entity("b"))
        Subject(Entity("a") to Entity("a'"), Entity("b") to Entity("b'"))(ast) shouldBe
          UnionAll(Entity("a'"), Entity("b'"))
      }
      "flat join" {
        val ast: XR = FlatJoin(JoinType.Inner, Entity("a"), Ident("b"), Ident("c"))
        Subject(Entity("a") to Entity("a'"), Ident("c") to Ident("c'"))(ast) shouldBe
          FlatJoin(JoinType.Inner, Entity("a'"), Ident("b"), Ident("c'"))
      }
      "distinct" {
        val ast: XR = Distinct(Entity("a"))
        Subject(Entity("a") to Entity("a'"))(ast) shouldBe Distinct(Entity("a'"))
      }
    }

    "expression" - {
      "unary" {
        val ast: XR = UnaryOp(BooleanOperator.not, Ident("a"))
        Subject(Ident("a") to Ident("a'"))(ast) shouldBe
          UnaryOp(BooleanOperator.not, Ident("a'"))
      }
      "binary" {
        val ast: XR = BinaryOp(Ident("a"), BooleanOperator.and, Ident("b"))
        Subject(Ident("a") to Ident("a'"), Ident("b") to Ident("b'"))(ast) shouldBe
          BinaryOp(Ident("a'"), BooleanOperator.and, Ident("b'"))
      }
      "function apply" {
        val fun1 = XR.Function1(Ident("a"), Ident("a"))
        val fun1Prime = XR.Function1(Ident("'a"), Ident("'a"))
        val ast: XR = FunctionApply(fun1, listOf(Ident("b"), Ident("c")))
        Subject(fun1 to fun1Prime, Ident("b") to Ident("b'"), Ident("c") to Ident("c'"))(ast) shouldBe
          FunctionApply(fun1Prime, listOf(Ident("b'"), Ident("c'")))
      }
      "constant" {
        val ast: XR = XR.Const.String("a")
        Subject()(ast) shouldBe ast
      }
      "null" {
        val ast: XR = XR.Const.Null()
        Subject()(ast) shouldBe ast
      }
      "product" {
        val ast: XR = Product("CC", listOf("foo" to Ident("a"), "bar" to Ident("b"), "baz" to Ident("c")))
        Subject(Ident("a") to Ident("a'"), Ident("b") to Ident("b'"), Ident("c") to Ident("c'"))(ast) shouldBe
          Product("CC", listOf("foo" to Ident("a'"), "bar" to Ident("b'"), "baz" to Ident("c'")))
      }
      "ident" {
        val ast: XR = Ident("a")
        Subject(Ident("a") to Ident("a'"))(ast) shouldBe
          Ident("a'")
      }
      "property" {
        val ast: XR = Property(Ident("a"), "b")
        Subject(Ident("a") to Ident("a'"))(ast) shouldBe
          Property(Ident("a'"), "b")
      }
      "when" {
        val ast: XR = When(listOf(
          Branch(Ident("a"), Ident("b")),
          Branch(Ident("c"), Ident("d"))
        ), Ident("e"))
        Subject(
          Ident("a") to Ident("a'"),
          Ident("b") to Ident("b'"),
          Ident("c") to Ident("c'"),
          Ident("d") to Ident("d'"),
          Ident("e") to Ident("e'"))(ast) shouldBe
            When(listOf(
              Branch(Ident("a'"), Ident("b'")),
              Branch(Ident("c'"), Ident("d'"))
            ), Ident("e'"))
      }
    }

    // When a variable declartion is reduced the actual variable should not change, only the right-hand-side
    "variable" {
      val ast: XR = XR.Variable(Ident("x"), Ident("a"))
      Subject(Ident("a") to Ident("a'"), Ident("x") to Ident("x'"))(ast) shouldBe
        XR.Variable(Ident("x"), Ident("a'"))
    }
    // Same thing with functions
    "function1" {
      val ast: XR = XR.Function1(Ident("a"), Ident("a"))
      Subject(Ident("a") to Ident("a'"))(ast) shouldBe
        XR.Function1(Ident("a"), Ident("a'"))
    }
    "functionN" {
      val ast: XR = XR.FunctionN(listOf(Ident("a"), Ident("b")), Ident("a"))
      Subject(Ident("a") to Ident("a'"), Ident("b") to Ident("b'"))(ast) shouldBe
        XR.FunctionN(listOf(Ident("a"), Ident("b")), Ident("a'"))
    }
    // Same thing with blocks, only the value should be replaced, not the variable
    "block" {
      val ast: XR = Block(
        listOf(
          Variable(Ident("a"), Ident("a")),
          Variable(Ident("b"), Ident("b"))
        ),
        Ident("a") `+==+` Ident("b")
      )
      Subject(Ident("a") to Ident("a'"), Ident("b") to Ident("b'"))(ast) shouldBe
        Block(
          listOf(
            Variable(Ident("a"), Ident("a'")),
            Variable(Ident("b"), Ident("b'"))
          ),
          Ident("a'") `+==+` Ident("b'")
        )
    }
  }
})