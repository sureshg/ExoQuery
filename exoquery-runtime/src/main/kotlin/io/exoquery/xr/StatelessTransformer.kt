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

  fun invokeIdent(xr: XR.Ident) = xr

  operator fun invoke(xr: XR.Variable): XR.Variable = with(xr) { Variable.cs(name, invoke(rhs)) }
  operator fun invoke(xr: XR.Branch): XR.Branch = with(xr) { XR.Branch.cs(invoke(cond), invoke(then)) }
  operator fun invoke(xr: XR.Block): XR.Block = with(xr) { Block.cs(stmts.map { invoke(it) }, invoke(output)) }

  operator fun invoke(xr: XR.Expression): XR.Expression =
    with(xr) {
      when (this) {
        is BinaryOp -> BinaryOp.cs(invoke(a), op, invoke(b))
        is Const -> this
        is Function1 -> Function1.cs(param, invoke(body))
        is FunctionN -> FunctionN.cs(params, invoke(body))
        is FunctionApply -> FunctionApply.cs(invoke(function), args.map { invoke(it) })
        is Ident -> invokeIdent(this)
        is IdentOrigin -> this
        is Property -> Property.cs(invoke(of), name)
        is UnaryOp -> UnaryOp.cs(op, invoke(expr))
        is Const.Null -> this
        is When -> When.cs(branches.map { invoke(it) }, invoke(orElse))
        is Block -> invoke(this)
        is Product -> Product.cs(fields.map { it.first to invoke(it.second) })
        // Infix can both be Expression and Query
        is Infix -> Infix(parts, params.map { invoke(it) }, pure, transparent, type, loc)
        is Aggregation -> Aggregation.cs(op, invoke(expr))
        is MethodCall -> MethodCall.cs(invoke(head), args.map { invoke(it) })
        is GlobalCall -> GlobalCall.cs(args.map { invoke(it) })
        is ValueOf -> ValueOf.cs(invoke(head))
        // The below must go in Function/Query/Expression/Action invoke clauses
        is Marker -> this
      }
    }

  operator fun invoke(xr: XR.Query): XR.Query =
    with(xr) {
      when (this) {
        is FlatMap -> FlatMap.cs(invoke(head), invokeIdent(id), invoke(body))
        is XR.Map -> XR.Map.cs(invoke(head), invokeIdent(id), invoke(body))
        is Entity -> this
        is Filter -> Filter.cs(invoke(head), invokeIdent(id), invoke(body))
        is Union -> Union.cs(invoke(a), invoke(b))
        is UnionAll -> UnionAll.cs(invoke(a), invoke(b))
        is Distinct -> Distinct.cs(invoke(head))
        is DistinctOn -> DistinctOn.cs(invoke(head), invokeIdent(id), invoke(by))
        is Drop -> Drop.cs(invoke(head), invoke(num))
        is SortBy -> SortBy.cs(invoke(head), invokeIdent(id), invoke(criteria), ordering)
        is Take -> Take.cs(invoke(head), invoke(num))
        is FlatJoin -> FlatJoin.cs(invoke(head), invokeIdent(id), invoke(on))
        is FlatGroupBy -> FlatGroupBy.cs(invoke(by))
        is FlatSortBy -> FlatSortBy.cs(invoke(by), ordering)
        is FlatFilter -> FlatFilter.cs(invoke(by))
        is ConcatMap -> ConcatMap.cs(invoke(head), invokeIdent(id), invoke(body))
        is GroupByMap -> GroupByMap.cs(invoke(head), invokeIdent(byAlias), invoke(byBody), invokeIdent(mapAlias), invoke(mapBody))
        is Nested -> Nested.cs(invoke(head))
        // Infix can both be Expression and Query
        is Infix -> Infix.cs(parts, params.map { invoke(it) })
        // The below must go in Function/Query/Expression/Action invoke clauses
        is Marker -> this
        // If there is a runtime bind, can't do anything with it
        is RuntimeQueryBind -> this
      }
    }
}