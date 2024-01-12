package io.exoquery.plugin.trees

import io.exoquery.xr.XR.*
import io.exoquery.xr.XR

interface StatefulTransformer<T> {
  val state: T

  fun apply(xr: XR.Function): Pair<XR.Function, StatefulTransformer<T>> =
    with(xr) {
      when (this) {
        is Function1 -> {
          val (bodyA, stateBody) = apply(body)
          Function1(param, bodyA) to stateBody
        }
        is FunctionN -> {
          val (bodyA, stateBody) = apply(body)
          FunctionN(params, bodyA) to stateBody
        }
        is Marker -> this to this@StatefulTransformer
      }
    }

  fun apply(xr: XR.Branch): Pair<XR.Branch, StatefulTransformer<T>> =
    with(xr) {
      val (condA, stateCond) = apply(cond)
      val (thenA, stateThen) = stateCond.apply(then)
      XR.Branch(condA, thenA) to stateThen
    }

  fun apply(xr: XR.Expression): Pair<XR.Expression, StatefulTransformer<T>> =
    with(xr) {
      when (this) {
        is BinaryOp -> {
          val (a, stateA) = apply(a)
          val (b, stateB) = stateA.apply(b)
          BinaryOp(a, op, b) to stateB
        }
        is FunctionApply -> {
          val (functionA, stateA) = apply(function)
          val (argsA, stateB) = stateA.applyList(args) { t, v -> t.apply(v) }
          FunctionApply(functionA, argsA) to stateB
        }
        is Property -> {
          val (ofA, stateA) = apply(of)
          Property(ofA, name) to stateA
        }
        is UnaryOp -> {
          val (exprA, stateA) = apply(expr)
          UnaryOp(op, exprA) to stateA
        }
        is When -> {
          val (branchesA, stateA) = applyList(branches) { t, v -> t.apply(v) }
          val (orElseA, stateB) = stateA.apply(orElse)
          When(branchesA, orElseA) to stateB
        }
        is Product -> {
          val (keys, values) = fields.unzip()
          val (valuesA, stateA) = applyList(values) { t, v -> t.apply(v) }
          Product(name, keys zip valuesA) to stateA
        }
        is Const -> this to this@StatefulTransformer
        is Ident -> this to this@StatefulTransformer
        Const.Null -> this to this@StatefulTransformer
        // The below must go in Function/Query/Expression/Action apply clauses
        is Marker -> this to this@StatefulTransformer
      }
    }

  fun apply(xr: XR): Pair<XR, StatefulTransformer<T>> =
    with(xr) {
      when (this) {
        is XR.Expression -> apply(this)
        is XR.Query -> apply(this)
        is XR.Function -> apply(this)
        // is XR.Action -> this.lift()
        is XR.Block -> apply(this)
        is XR.Branch -> apply(this)
        is XR.Variable -> apply(this)
      }
    }

  fun <U, R> applyList(list: List<U>, f: (StatefulTransformer<T>, U) -> Pair<R, StatefulTransformer<T>>): Pair<List<R>,  StatefulTransformer<T>> {
    val (newList, transformer) =
      list.fold(Pair(mutableListOf<R>(), this)) { (values, t), v ->
        val (vt, vtt) = f(t, v)
        values += vt
        Pair(values, vtt)
      }

    return Pair(newList.toList(), transformer)
  }
}