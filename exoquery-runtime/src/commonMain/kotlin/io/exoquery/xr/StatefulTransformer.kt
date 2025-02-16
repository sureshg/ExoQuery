package io.exoquery.xr

//import io.exoquery.util.DebugMsg
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR.Map // Make sure to explicitly have this import or Scala will use Map the collection
import io.exoquery.xr.copy.*

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

//data class DebugDump(val info: MutableList<DebugMsg> = mutableListOf()){
//  fun dump(str: String) = info.add(DebugMsg.Fragment(str))
    //  companion object {
//    operator fun invoke(vararg msg: DebugMsg) = DebugDump(msg.toMutableList())
      //  }
//}

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
          BinaryOp.cs(a, op, b) to stateB
        }
        is FunctionN -> {
          val (bodyA, stateBody) = invoke(body)
          FunctionN.cs(params, bodyA) to stateBody
        }
        is FunctionApply -> {
          val (functionA, stateA) = invoke(function)
          val (argsA, stateB) = stateA.applyList(args) { t, v -> t.invoke(v) }
          FunctionApply.cs(functionA, argsA) to stateB
        }
        is Property -> {
          val (ofA, stateA) = invoke(of)
          Property.cs(ofA, name) to stateA
        }
        is UnaryOp -> {
          val (exprA, stateA) = invoke(expr)
          UnaryOp.cs(op, exprA) to stateA
        }
        is When -> {
          val (branchesA, stateA) = applyList(branches) { t, v -> t.invoke(v) }
          val (orElseA, stateB) = stateA.invoke(orElse)
          When.cs(branchesA, orElseA) to stateB
        }
        is Product -> {
          val (keys, values) = fields.unzip()
          val (valuesA, stateA) = applyList(values) { t, v -> t.invoke(v) }
          Product.cs(keys zip valuesA) to stateA
        }
        // Infix can be both a Query and Expression
        is Infix -> {
          val (paramsA, stateA) = applyList(params) { t, v -> t.invoke(v) }
          Infix.cs(parts, paramsA) to stateA
        }
        is Aggregation -> {
          val (bodyA, stateA) = invoke(expr)
          Aggregation.cs(op, bodyA) to stateA
        }
        is MethodCall -> invoke(this)
        is GlobalCall -> invoke(this)
        is QueryToExpr -> {
          val (headA, stateA) = invoke(head)
          QueryToExpr.cs(headA) to stateA
        }
        is Block -> invoke(this)
        is Const -> this to this@StatefulTransformer
        is Ident -> invokeIdent(this)
        is Const.Null -> this to this@StatefulTransformer
        is TagForParam -> this to this@StatefulTransformer
        is TagForSqlExpression -> this to this@StatefulTransformer
      }
    }

  operator fun invoke(xr: XR.Query): Pair<XR.Query, StatefulTransformer<T>> =
    with(xr) {
      when (this) {
        is FlatMap -> {
          val (aA, stateA) = invoke(head)
          val (bA, stateB) = stateA.invoke(body)
          FlatMap.cs(aA, id, bA) to stateB
        }
        is Map -> {
          val (aA, stateA) = invoke(head)
          val (bA, stateB) = stateA.invoke(body)
          Map.cs(aA, id, bA) to stateB
        }
        is Entity -> this to this@StatefulTransformer
        is Filter -> {
          val (aA, stateA) = invoke(head)
          val (bA, stateB) = stateA.invoke(body)
          Filter.cs(aA, id, bA) to stateB
        }
        is Union -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          Union.cs(aA, bA) to stateB
        }
        is UnionAll -> {
          val (aA, stateA) = invoke(a)
          val (bA, stateB) = stateA.invoke(b)
          UnionAll.cs(aA, bA) to stateB
        }
        is Distinct -> {
          val (queryA, stateA) = invoke(head)
          Distinct.cs(queryA) to stateA
        }
        is DistinctOn -> {
          val (queryA, stateA) = invoke(head)
          val (byA, stateB) = stateA.invoke(by)
          DistinctOn.cs(queryA, id, byA) to stateB
        }
        is Drop -> {
          val (queryA, stateA) = invoke(head)
          val (numB, stateB) = stateA.invoke(num)
          Drop.cs(queryA, numB) to stateB
        }
        is SortBy -> {
          val (queryA, stateA) = invoke(head)
          val (criteriaA, stateB) = stateA.invoke(criteria)
          SortBy.cs(queryA, id, criteriaA, ordering) to stateB
        }
        is Take -> {
          val (queryA, stateA) = invoke(head)
          val (numB, stateB) = stateA.invoke(num)
          Take.cs(queryA, numB) to stateB
        }
        is FlatJoin -> {
          val (aA, stateA) = invoke(head)
          val (onA, stateB) = stateA.invoke(on)
          FlatJoin.cs(aA, id, onA) to stateB
        }
        is FlatGroupBy -> {
          val (aA, stateA) = invoke(by)
          FlatGroupBy.cs(aA) to stateA
        }
        is FlatSortBy -> {
          val (aA, stateA) = invoke(by)
          FlatSortBy.cs(aA, ordering) to stateA
        }
        is FlatFilter -> {
          val (aA, stateA) = invoke(by)
          FlatFilter.cs(aA) to stateA
        }
        is ConcatMap -> {
          val (aA, stateA) = invoke(head)
          val (bA, stateB) = stateA.invoke(body)
          ConcatMap.cs(aA, id, bA) to stateB
        }
        is Nested -> {
          val (queryA, stateA) = invoke(head)
          Nested.cs(queryA) to stateA
        }
        // Infix can be both a Query and Expression
        is Infix -> {
          val (paramsA, stateA) = applyList(params) { t, v -> t.invoke(v) }
          Infix(parts, paramsA, pure, transparent, type, loc) to stateA
        }
        is ExprToQuery -> {
          val (headA, stateA) = invoke(head)
          ExprToQuery.cs(headA) to stateA
        }
        is TagForSqlQuery -> this to this@StatefulTransformer
        is CustomQueryRef -> {
          val (cust, state) = customQuery.handleStatefulTransformer(this@StatefulTransformer)
          CustomQueryRef.cs(cust) to state
        }
        is FunctionApply -> invoke(this)
        is Ident -> invokeIdent(this)
        is GlobalCall -> invoke(this)
        is MethodCall -> invoke(this)
      }
    }

  fun invokeIdent(xr: Ident): Pair<Ident, StatefulTransformer<T>> =
    with(xr) {
      this to this@StatefulTransformer
    }

  operator fun invoke(xr: XR.U.QueryOrExpression): Pair<XR.U.QueryOrExpression, StatefulTransformer<T>> =
    when(xr) {
      is XR.Query -> invoke(xr)
      is XR.Expression -> invoke(xr)
    }

  operator fun invoke(xr: XR.FunctionN): Pair<XR.FunctionN, StatefulTransformer<T>> =
    with(xr) {
      val (bodyA, stateA) = invoke(body)
      FunctionN.cs(params, bodyA) to stateA
    }

  operator fun invoke(xr: XR.GlobalCall): Pair<XR.GlobalCall, StatefulTransformer<T>> =
    with(xr) {
      val (argsA, stateA) = applyList(args) { t, v -> t.invoke(v) }
      GlobalCall.cs(argsA) to stateA
    }

  operator fun invoke(xr: XR.MethodCall): Pair<XR.MethodCall, StatefulTransformer<T>> =
    with(xr) {
      val (headA, stateA) = invoke(head)
      val (argsA, stateB) = stateA.applyList(args) { t, v -> t.invoke(v) }
      MethodCall.cs(headA, argsA) to stateB
    }

  operator fun invoke(xr: XR.FunctionApply): Pair<XR.FunctionApply, StatefulTransformer<T>> =
    with(xr) {
      val (functionA, stateA) = invoke(function)
      val (argsA, stateB) = applyList(args) { t, v -> t.invoke(v) }
      FunctionApply.cs(functionA, argsA) to stateB
    }

  operator fun invoke(xr: XR.Block): Pair<XR.Block, StatefulTransformer<T>> =
    with(xr) {
      val (stmtsA, stateA) = applyList(stmts) { t, v -> t.invoke(v) }
      val (outputA, stateB) = stateA.invoke(output)
      Block.cs(stmtsA, outputA) to stateB
    }

  operator fun invoke(xr: XR.Branch): Pair<XR.Branch, StatefulTransformer<T>> =
    with(xr) {
      val (condA, stateA) = invoke(cond)
      val (thenA, stateB) = stateA.invoke(then)
      Branch.cs(condA, thenA) to stateB
    }

  operator fun invoke(xr: XR.Variable): Pair<XR.Variable, StatefulTransformer<T>> =
    with(xr) {
      val (rhsA, stateA) = invoke(rhs)
      Variable.cs(name, rhsA) to stateA
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
