package io.exoquery.xr

import io.exoquery.xr.XR.*

interface StatelessTransformerSingleRoot: StatelessTransformer {
  fun <X> root(xr: X): X where X: XR

  // Need to override the things that otherwise wouldn't go through the root
  override fun invoke(xr: Expression): Expression = super.invoke(root(xr))
  override fun invoke(xr: Query): Query = super.invoke(root(xr))
  override fun invoke(xr: Branch): Branch = super.invoke(root(xr))
  override fun invoke(xr: Variable): Variable = super.invoke(root(xr))
  override fun invoke(xr: XR): XR = super.invoke(root(xr))
}

interface StatelessTransformer {

  operator fun invoke(xr: XR): XR =
    with(xr) {
      when (this) {
        is XR.Expression -> invoke(this)
        is XR.Query -> invoke(this)
        // is XR.Action -> this.lift()
        is XR.Branch -> invoke(this)
        is XR.Variable -> invoke(this)
      }
    }

  operator fun invoke(xr: XR.Variable): XR.Variable = with(xr) { Variable(name, invoke(rhs)) }
  operator fun invoke(xr: XR.Branch): XR.Branch = with(xr) { XR.Branch(invoke(cond), invoke(then)) }
  operator fun invoke(xr: XR.Block): XR.Block = with(xr) { Block(stmts.map { invoke(it) }, invoke(output)) }

  operator fun invoke(xr: XR.Expression): XR.Expression =
    with(xr) {
      when (this) {
        is BinaryOp -> BinaryOp(invoke(a), op, invoke(b))
        is Const -> this
        is Function1 -> Function1(param, invoke(body))
        is FunctionN -> FunctionN(params, invoke(body))
        is FunctionApply -> FunctionApply(invoke(function), args.map { invoke(it) })
        is Ident -> this
        is IdentOrigin -> this
        is Property -> Property(invoke(of), name)
        is UnaryOp -> UnaryOp(op, invoke(expr))
        Const.Null -> this
        is When -> When(branches.map { invoke(it) }, invoke(orElse))
        is XR.Block -> invoke(this)
        is Product -> Product(name, fields.map { it.first to invoke(it.second) })
        // Infix can both be Expression and Query
        is Infix -> Infix(parts, params.map { invoke(it) }, pure, transparent, type)
        // The below must go in Function/Query/Expression/Action invoke clauses
        is Marker -> this
      }
    }

  operator fun invoke(xr: XR.Query): XR.Query =
    with(xr) {
      when (this) {
        is FlatMap -> FlatMap(invoke(head), id, invoke(body))
        is XR.Map -> XR.Map(invoke(head), id, invoke(body))
        is Entity -> this
        is Filter -> Filter(invoke(head), id, invoke(body))
        is Union -> Union(invoke(a), invoke(b))
        is UnionAll -> UnionAll(invoke(head), invoke(body))
        is Distinct -> Distinct(invoke(head))
        is DistinctOn -> DistinctOn(invoke(head), id, invoke(by))
        is Drop -> Drop(invoke(head), invoke(num))
        is SortBy -> SortBy(invoke(head), id, invoke(criteria), ordering)
        is Take -> Take(invoke(head), invoke(num))
        is FlatJoin -> FlatJoin(joinType, invoke(head), id, invoke(on))
        is ConcatMap -> ConcatMap(invoke(head), id, invoke(body))
        is GroupByMap -> GroupByMap(invoke(head), byAlias, invoke(byBody), mapAlias, invoke(mapBody))
        is Aggregation -> Aggregation(operator, invoke(body))
        is Nested -> Nested(invoke(head))
        // Infix can both be Expression and Query
        is Infix -> Infix(parts, params.map { invoke(it) }, pure, transparent, type)
        // The below must go in Function/Query/Expression/Action invoke clauses
        is Marker -> this
      }
    }
}