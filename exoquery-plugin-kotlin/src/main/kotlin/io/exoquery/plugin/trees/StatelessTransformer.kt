package io.exoquery.plugin.trees

import io.exoquery.xr.XR.*
import io.exoquery.xr.XR

// I think beta reduction should be used for XR.Expression in all the cases, shuold
// move forward and find out if this is actually the case. If it is not, can probably
// just add `case ast if map.contains(ast) =>` to the apply for Query, Function, etc...
// maybe should have separate maps for Query, Function, etc... for that reason if those cases even exist
data class BetaReduction(val map: Map<XR.Expression, XR.Expression>): StatelessTransformer {
  override fun apply(xr: XR.Expression): XR.Expression =
    when {
      map.contains(xr) -> {
        val rep = BetaReduction(map - xr - map[xr]!!).apply(map[xr]!!)
        when {

        }

        TODO()
      }

      else -> TODO()
    }


}


interface StatelessTransformer {

  fun apply(xr: XR.Branch): XR.Branch =
    with(xr) {
      XR.Branch(apply(cond), apply(then))
    }

  fun apply(xr: XR.Function): XR.Function =
    with(xr) {
      when (this) {
        is Function1 -> Function1(param, apply(body))
        is FunctionN -> FunctionN(params, apply(body))
        is Marker -> this
      }
    }

  fun apply(xr: XR.Expression): XR.Expression =
    with(xr) {
      when (this) {
        is BinaryOp -> BinaryOp(apply(a), op, apply(b))
        is Const -> this
        is FunctionApply -> FunctionApply(apply(function), args.map { apply(it) })
        is Ident -> this
        is Property -> Property(apply(of), name)
        is UnaryOp -> UnaryOp(op, apply(expr))
        Const.Null -> this
        is When -> When(branches.map { apply(it) }, apply(orElse))
        // The below must go in Function/Query/Expression/Action apply clauses
        is Marker -> this
      }
    }

  fun apply(xr: XR.Query): XR.Query =
    with(xr) {
      when (this) {
        is FlatMap -> FlatMap(apply(a), ident, apply(b))
        is XR.Map -> XR.Map(apply(a), ident, apply(b))
        is Entity -> this
        is Filter -> Filter(apply(a), ident, apply(b))
        is Union -> Union(apply(a), apply(b))
        is UnionAll -> UnionAll(apply(a), apply(b))
        is Distinct -> Distinct(apply(query))
        is DistinctOn -> DistinctOn(apply(query), alias, apply(by))
        is Drop -> Drop(apply(query), num)
        is SortBy -> SortBy(apply(query), alias, apply(criteria), ordering)
        is Take -> Take(apply(query), num)
        is FlatJoin -> FlatJoin(joinType, apply(a), aliasA, apply(on))
        is ConcatMap -> ConcatMap(apply(a), ident, apply(b))
        is GroupByMap -> GroupByMap(apply(query), byAlias, apply(byBody), mapAlias, apply(mapBody))
        is Aggregation -> Aggregation(operator, body)
        is Nested -> Nested(apply(query))
        // The below must go in Function/Query/Expression/Action apply clauses
        is Marker -> this
      }
    }

  fun apply(xr: XR.Variable): XR.Variable = with(xr) { Variable(name, apply(rhs)) }
  fun apply(xr: XR.Block): XR.Block = with(xr) { Block(stmts.map { apply(it) }, apply(output)) }


  fun apply(xr: XR): XR =
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

}