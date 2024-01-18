package io.exoquery.xr

import io.exoquery.xr.XR.*


/**
 * Normally being able to access all XR elements in a StatefulTransformer
 * requires overriding: invoke(Expression), invoke(Query), invoke(Branch),
 * and invoke(Variable) and invoke(XR) for the other possibilities.
 * This is because in the ExoQuery transformation model
 * invoke calls on a node e.g. invoke(mapQuery.a) directly go to the invoke
 * of that node type invoke(Query) as opposed to the root-level method
 * invoke(XR). This stateful transformer extension adds an additional method
 * `root(XR)` which all elements will go through before going to the corresponding
 * branch-level invoke functions.
 */
interface StatefulTransformerSingleRoot<T>: StatefulTransformer<T> {
  fun <X> root(xr: X): Pair<X, StatefulTransformerSingleRoot<T>, > where X: XR

  private fun invokeSuper(xr: Expression): Pair<Expression, StatefulTransformer<T>> = super.invoke(xr)
  override fun invoke(xr: Expression): Pair<Expression, StatefulTransformer<T>> {
    val (a, stateA) = root(xr)
    return stateA.invokeSuper(a)
  }

  private fun invokeSuper(xr: Query): Pair<Query, StatefulTransformer<T>> = super.invoke(xr)
  override fun invoke(xr: Query): Pair<Query, StatefulTransformer<T>> {
    val (a, stateA) = root(xr)
    return stateA.invokeSuper(a)
  }

  private fun invokeSuper(xr: Branch): Pair<Branch, StatefulTransformer<T>> = super.invoke(xr)
  override fun invoke(xr: Branch): Pair<Branch, StatefulTransformer<T>> {
    val (a, stateA) = root(xr)
    return stateA.invokeSuper(a)
  }

  private fun invokeSuper(xr: Variable): Pair<Variable, StatefulTransformer<T>> = super.invoke(xr)
  override fun invoke(xr: Variable): Pair<Variable, StatefulTransformer<T>> {
    val (a, stateA) = root(xr)
    return stateA.invokeSuper(a)
  }

  private fun invokeSuper(xr: XR): Pair<XR, StatefulTransformer<T>> = super.invoke(xr)
  override fun invoke(xr: XR): Pair<XR, StatefulTransformer<T>> {
    val (a, stateA) = root(xr)
    return stateA.invokeSuper(a)
  }
}

interface StatefulTransformer<T> {
  val state: T

  operator fun invoke(xr: XR): Pair<XR, StatefulTransformer<T>> =
    with(xr) {
      when (this) {
        is XR.Expression -> invoke(this)
        is XR.Query -> invoke(this)
        // is XR.Action -> this.lift()
        is XR.Branch -> invoke(this)
        is XR.Variable -> invoke(this)
      }
    }

  operator fun invoke(xr: XR.Expression): Pair<XR.Expression, StatefulTransformer<T>> =
    with(xr) {
      when (this) {
        is BinaryOp -> {
          val (a, stateA) = invoke(a)
          val (b, stateB) = stateA.invoke(b)
          BinaryOp(a, op, b) to stateB
        }
        is Function1 -> {
          val (bodyA, stateBody) = invoke(body)
          Function1(param, bodyA) to stateBody
        }
        is FunctionN -> {
          val (bodyA, stateBody) = invoke(body)
          FunctionN(params, bodyA) to stateBody
        }
        is FunctionApply -> {
          val (functionA, stateA) = invoke(function)
          val (argsA, stateB) = stateA.applyList(args) { t, v -> t.invoke(v) }
          FunctionApply(functionA, argsA) to stateB
        }
        is Property -> {
          val (ofA, stateA) = invoke(of)
          Property(ofA, name) to stateA
        }
        is UnaryOp -> {
          val (exprA, stateA) = invoke(expr)
          UnaryOp(op, exprA) to stateA
        }
        is When -> {
          val (branchesA, stateA) = applyList(branches) { t, v -> t.invoke(v) }
          val (orElseA, stateB) = stateA.invoke(orElse)
          When(branchesA, orElseA) to stateB
        }
        is Product -> {
          val (keys, values) = fields.unzip()
          val (valuesA, stateA) = applyList(values) { t, v -> t.invoke(v) }
          Product(name, keys zip valuesA) to stateA
        }
        is XR.Block -> invoke(this)
        is Const -> this to this@StatefulTransformer
        is Ident -> this to this@StatefulTransformer
        is IdentOrigin -> this to this@StatefulTransformer
        Const.Null -> this to this@StatefulTransformer
        // The below must go in Function/Query/Expression/Action invoke clauses
        is Marker -> this to this@StatefulTransformer
      }
    }

  operator fun invoke(xr: XR.Query): Pair<XR.Query, StatefulTransformer<T>> =
    with(xr) {
      when (this) {
        is FlatMap -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          FlatMap(aA, ident, bA) to stateB
        }
        is XR.Map -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          XR.Map(aA, ident, bA) to stateB
        }
        is Entity -> this to this@StatefulTransformer
        is Filter -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          Filter(aA, ident, bA) to stateB
        }
        is Union -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          Union(aA, bA) to stateB
        }
        is UnionAll -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          UnionAll(aA, bA) to stateB
        }
        is Distinct -> {
          val (queryA, stateA) = invoke(query)
          Distinct(queryA) to stateA
        }
        is DistinctOn -> {
          val (queryA, stateA) = invoke(query)
          val (byA, stateB) = stateA.invoke(by)
          DistinctOn(queryA, alias, byA) to stateB
        }
        is Drop -> {
          val (queryA, stateA) = invoke(query)
          val (numB, stateB) = stateA.invoke(num)
          Drop(queryA, numB) to stateB
        }
        is SortBy -> {
          val (queryA, stateA) = invoke(query)
          val (criteriaA, stateB) = stateA.invoke(criteria)
          SortBy(queryA, alias, criteriaA, ordering) to stateB
        }
        is Take -> {
          val (queryA, stateA) = invoke(query)
          val (numB, stateB) = stateA.invoke(num)
          Take(queryA, numB) to stateB
        }
        is FlatJoin -> {
          val (aA, stateA) = invoke(a)
          val (onA, stateB) = stateA.invoke(on)
          FlatJoin(joinType, aA, aliasA, onA) to stateB
        }
        is ConcatMap -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          ConcatMap(aA, ident, bA) to stateB
        }
        is GroupByMap -> {
          val (queryA, stateA) = invoke(query)
          val (byBodyA, stateB) = stateA.invoke(byBody)
          val (mapBodyA, stateC) = stateB.invoke(mapBody)
          GroupByMap(queryA, byAlias, byBodyA, mapAlias, mapBodyA) to stateC
        }
        is Aggregation -> {
          val (bodyA, stateA) = invoke(body)
          Aggregation(operator, bodyA) to stateA
        }
        is Nested -> {
          val (queryA, stateA) = invoke(query)
          Nested(queryA) to stateA
        }
        // The below must go in Function/Query/Expression/Action invoke clauses
        is Marker -> this to this@StatefulTransformer
      }
    }

  operator fun invoke(xr: XR.Block): Pair<XR.Block, StatefulTransformer<T>> =
    with(xr) {
      val (stmtsA, stateA) = applyList(stmts) { t, v -> t.invoke(v) }
      val (outputA, stateB) = stateA.invoke(output)
      Block(stmtsA, outputA) to stateB
    }

  operator fun invoke(xr: XR.Branch): Pair<XR.Branch, StatefulTransformer<T>> =
    with(xr) {
      val (condA, stateA) = invoke(cond)
      val (thenA, stateB) = stateA.invoke(then)
      Branch(condA, thenA) to stateB
    }

  operator fun invoke(xr: XR.Variable): Pair<XR.Variable, StatefulTransformer<T>> =
    with(xr) {
      val (rhsA, stateA) = invoke(rhs)
      Variable(name, rhsA) to stateA
    }

  fun <U, R> applyList(list: List<U>, f: (StatefulTransformer<T>, U) -> Pair<R, StatefulTransformer<T>>): Pair<List<R>, StatefulTransformer<T>> {
    val (newList, transformer) =
      list.fold(Pair(mutableListOf<R>(), this)) { (values, t), v ->
        val (vt, vtt) = f(t, v)
        values += vt
        Pair(values, vtt)
      }

    return Pair(newList.toList(), transformer)
  }
}