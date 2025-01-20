package io.exoquery.xr

import io.exoquery.xr.XR.*
import io.exoquery.xr.XR.Map // Make sure to explicitly have this import or Scala will use Map the collection

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
        is XR.Branch -> invoke(this)
        is XR.Variable -> invoke(this)
      }
    }

  fun invokeIdent(xr: XR.Ident) = xr

  operator fun invoke(xr: XR.Variable): XR.Variable = with(xr) { Variable.csf(name, invoke(rhs))(this) }
  operator fun invoke(xr: XR.Branch): XR.Branch = with(xr) { XR.Branch.csf(invoke(cond), invoke(then))(this) }
  operator fun invoke(xr: XR.Block): XR.Block = with(xr) { Block.csf(stmts.map { invoke(it) }, invoke(output))(this) }

  operator fun invoke(xr: XR.Expression): XR.Expression =
    with(xr) {
      when (this) {
        is BinaryOp -> BinaryOp.csf(invoke(a), op, invoke(b))(this)
        is Const -> this
        is Function1 -> Function1.csf(param, invoke(body))(this)
        is FunctionN -> FunctionN.csf(params, invoke(body))(this)
        is FunctionApply -> FunctionApply.csf(invoke(function), args.map { invoke(it) })(this)
        is Ident -> invokeIdent(this)
        is Property -> Property.csf(invoke(of), name)(this)
        is UnaryOp -> UnaryOp.csf(op, invoke(expr))(this)
        is Const.Null -> this
        is When -> When.csf(branches.map { invoke(it) }, invoke(orElse))(this)
        is Block -> invoke(this)
        is Product -> Product.csf(fields.map { it.first to invoke(it.second) })(this)
        // Infix can both be Expression and Query
        is Infix -> Infix(parts, params.map { invoke(it) }, pure, transparent, type, loc)
        is Aggregation -> Aggregation.csf(op, invoke(expr))(this)
        is MethodCall -> MethodCall.csf(invoke(head), args.map { invoke(it) })(this)
        is GlobalCall -> GlobalCall.csf(args.map { invoke(it) })(this)
        is ValueOf -> ValueOf.csf(invoke(head))(this)

        is TagForParam -> this
        is TagForSqlExpression -> this
      }
    }

  operator fun invoke(xr: XR.Query): XR.Query =
    with(xr) {
      when (this) {
        is FlatMap -> FlatMap.csf(invoke(head), invokeIdent(id), invoke(body))(this)
        is Map -> Map.csf(invoke(head), invokeIdent(id), invoke(body))(this)
        is Entity -> this
        is Filter -> Filter.csf(invoke(head), invokeIdent(id), invoke(body))(this)
        is Union -> Union.csf(invoke(a), invoke(b))(this)
        is UnionAll -> UnionAll.csf(invoke(a), invoke(b))(this)
        is Distinct -> Distinct.csf(invoke(head))(this)
        is DistinctOn -> DistinctOn.csf(invoke(head), invokeIdent(id), invoke(by))(this)
        is Drop -> Drop.csf(invoke(head), invoke(num))(this)
        is SortBy -> SortBy.csf(invoke(head), invokeIdent(id), invoke(criteria), ordering)(this)
        is Take -> Take.csf(invoke(head), invoke(num))(this)
        is FlatJoin -> FlatJoin.csf(invoke(head), invokeIdent(id), invoke(on))(this)
        is FlatGroupBy -> FlatGroupBy.csf(invoke(by))(this)
        is FlatSortBy -> FlatSortBy.csf(invoke(by), ordering)(this)
        is FlatFilter -> FlatFilter.csf(invoke(by))(this)
        is ConcatMap -> ConcatMap.csf(invoke(head), invokeIdent(id), invoke(body))(this)
        is GroupByMap -> GroupByMap.csf(invoke(head), invokeIdent(byAlias), invoke(byBody), invokeIdent(mapAlias), invoke(mapBody))(this)
        is Nested -> Nested.csf(invoke(head))(this)
        is QueryOf -> QueryOf.csf(invoke(head))(this)
        // Infix can both be Expression and Query
        is Infix -> Infix.csf(parts, params.map { invoke(it) })(this)
        is TagForSqlQuery -> this
        is CustomQueryRef -> CustomQueryRef.csf(customQuery.handleStatelessTransform(this@StatelessTransformer))(this)
      }
    }
}