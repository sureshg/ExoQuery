package io.exoquery.sql

import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.XR
import io.exoquery.xr.XR.FlatMap
import io.exoquery.xr.XR.Map
import io.exoquery.xr.XR.FlatJoin
import io.exoquery.xr.XR.Ordering.PropertyOrdering
import io.exoquery.xr.XRType
import io.exoquery.xr.containsImpurities
import io.exoquery.xrError

final data class OrderByCriteria(val ast: XR, val ordering: PropertyOrdering)

sealed interface FromContext { val type: XRType }
final data class TableContext(val entity: XR.Entity, val alias: String) : FromContext {
  override val type: XRType = entity.type
}

final data class QueryContext(val query: SqlQuery, val alias: String) : FromContext {
  override val type: XRType = query.type
}

final data class InfixContext(val infix: XR.Infix, val alias: String) : FromContext {
  override val type: XRType = infix.type
}

final data class FlatJoinContext(val t: XR.JoinType, val a: FromContext, val on: XR) : FromContext {
  override val type: XRType = a.type
}

sealed interface SqlQuery {
  val type: XRType
}

sealed interface SetOperation
object UnionOperation : SetOperation
object UnionAllOperation : SetOperation

sealed interface DistinctKind {
  val isDistinct: Boolean

  object Distinct : DistinctKind { override val isDistinct: Boolean = true }
  data class DistinctOn(val props: List<XR>) : DistinctKind { override val isDistinct: Boolean = true }
  object None : DistinctKind { override val isDistinct: Boolean = false }
}

final data class SetOperationSqlQuery(
  val a: SqlQuery,
  val op: SetOperation,
  val b: SqlQuery,
  override val type: XRType
): SqlQuery

final data class UnaryOperationSqlQuery(
  val op: XR.UnaryOp,
  val q: SqlQuery,
  override val type: XRType
): SqlQuery

final data class SelectValue(val ast: XR, val alias: String? = null, val concat: Boolean = false) {
  val type: XRType = ast.type
}

final data class FlattenSqlQuery(
  val from: List<FromContext> = emptyList(),
  val where: XR? = null,
  val groupBy: XR? = null,
  val orderBy: List<OrderByCriteria> = emptyList(),
  val limit: XR? = null,
  val offset: XR? = null,
  val select: List<SelectValue> = emptyList(),
  val distinct: DistinctKind = DistinctKind.None,
  override val type: XRType
): SqlQuery

class SqlQueryApply(val traceConfig: TraceConfig) {
  val trace: Tracer = Tracer(TraceType.SqlQueryConstruct, traceConfig, 1)

  fun invoke(query: XR): SqlQuery =
    with(query) {
      when {
        this is XR.Union ->
          trace("Construct SqlQuery from: Union") andReturn {
            SetOperationSqlQuery(invoke(a), UnionOperation, invoke(b), query.type)
          }
        this is XR.UnionAll ->
          trace("Construct SqlQuery from: UnionAll") andReturn {
            SetOperationSqlQuery(invoke(head), UnionAllOperation, invoke(body), query.type)
          }
        this is XR.UnaryOp && this.expr is XR.Query ->
          trace("Construct SqlQuery from: UnaryOp") andReturn {
            UnaryOperationSqlQuery(this, invoke(this.expr), query.type)
          }
        // e.g. if the XR is a "a + b" make it into a `Select a + b`
        this is XR.Expression ->
          trace("Construct SqlQuery from: Expression") andReturn {
            FlattenSqlQuery(select = listOf(SelectValue(this)), type = query.type)
          }
        // e.g. if the XR is `Map(Ent(person), n, n)` then we just need the Ent(person) part
        this is XR.Map && id == body ->
          trace("Construct SqlQuery from: Map") andReturn {
            invoke(head)
          }
        // e.g. Take(people.flatMap(p => addresses:Query), 3) -> `select ... from people, addresses... limit 3`
        this is XR.Take && head is XR.FlatMap ->
          trace("Construct SqlQuery from: TakeDropFlatten") andReturn {
            flatten(head, "x").copy(limit = num, type = query.type)
          }
        // e.g. Drop(people.flatMap(p => addresses:Query), 3) -> `select ... from people, addresses... offset 3`
        this is XR.Drop && head is XR.FlatMap ->
          trace("Construct SqlQuery from: TakeDropFlatten") andReturn {
            flatten(head, "x").copy(offset = num, type = query.type)
          }
        this is XR.Query ->
          trace("Construct SqlQuery from: Query") andReturn {
            flatten(this, "x")
          }
        this is XR.Infix ->
          trace("Construct SqlQuery from: Infix") andReturn {
            flatten(this, "x")
          }
        else ->
          trace("[INVALID] Construct SqlQuery from: other") andReturn {
            xrError("Query not properly normalized. Please open a bug report. Ast: '$query'")
          }
      }
    }

  private fun flattenContexts(query: XR): Pair<List<FromContext>, XR> =
    with(query) {
      when {
        // A flat-join query with no maps e.g: `qr1.flatMap(e1 => qr1.join(e2 => e1.i == e2.i))`
        this is FlatMap && body is FlatJoin ->
          trace("Flattening FlatMap with FlatJoin") andReturn {
            val cc: XR.Product = XR.Product.fromType(body.type, body.id.name)
            flattenContexts(FlatMap(head, id, Map(body, body.id, cc)))
          }
        this is FlatMap && body is XR.Infix ->
          trace("[INVALID] Flattening Flatmap with Infix") andReturn {
            xrError("Infix can't be use as a `flatMap` body. $query")
          }
        this is FlatMap ->
          trace("Flattening Flatmap with Query") andReturn {
            val source                             = source(head, id.name)
            val (nestedContexts, finalFlatMapBody) = flattenContexts(body)
            (listOf(source) + nestedContexts to finalFlatMapBody)
          }
        else ->
          trace("Flattening other") andReturn {
            (listOf<FromContext>() to query)
          }
      }
    }

  private fun flatten(query: XR, alias: String): FlattenSqlQuery =
    trace("Flattening ${query}") andReturn {
      val (sources, finalFlatMapBody) = flattenContexts(query)
      flatten(sources, finalFlatMapBody, alias, nestNextMap = false)
    }

  private fun flatten(
    sources: List<FromContext>,
    finalFlatMapBody: XR,
    alias: String,
    nestNextMap: Boolean
  ): FlattenSqlQuery {

    fun select(alias: String, quat: XRType): List<SelectValue> = listOf(SelectValue(XR.Ident(alias, quat), null))

    fun base(query: XR, alias: String, nestNextMap: Boolean): FlattenSqlQuery =
      trace("Computing Base (nestingMaps=${nestNextMap}) for Query: $query") andReturn {
        fun nest(ctx: FromContext): FlattenSqlQuery = trace("Computing FlattenSqlQuery for: $ctx") andReturn {
          FlattenSqlQuery(from = sources + ctx, select = select(alias, query.type), type = query.type)
        }
        with(query) {
          when {
            this is XR.GroupByMap -> trace("base| Nesting GroupByMap $query") andReturn { nest(source(query, alias)) }

            // A map that contains mapped-to aggregations e.g.
            //   people.map(p=>max(p.name))
            // Could have more complex structures e.g: people.map(p=>SomeCaseClassOrTuple(max(p.name),min(p.age)))
            // so we need to search for the aggregations.
            // Also it could have impure-infixes inside e.g.
            //   people.map(p=>SomeCaseClassOrTuple(p.name, someStatefulSqlFunction(p.age)))
            // therefore we need to check if there are elements like this inside of the map-function and nest it
            this is XR.Map && containsImpurities() ->
              trace("base| Nesting Map(a=>ContainsImpurities(a)) $query") andReturn { nest(source(query, alias)) }

            this is XR.Nested -> trace("base| Nesting Nested $query") andReturn { nest(source(head, alias)) }

            this is XR.ConcatMap -> trace("base| Nesting ConcatMap $query") andReturn { nest(source(this, alias)) }

            this is XR.Filter -> trace("base| Flattening Filter $query") andReturn { flatten(sources, query, alias, nestNextMap) }
            this is XR.Entity -> trace("base| Flattening Entity $query") andReturn { flatten(sources, query, alias, nestNextMap) }

            this is XR.Map && nestNextMap ->
              trace("base| Map + nest $query") andReturn { nest(source(query, alias)) }
            this is XR.Map ->
              trace("base| Map $query") andReturn { flatten(sources, query, alias, nestNextMap) }

            sources.isEmpty() ->
              trace("base| Flattening Empty-Sources $query") andReturn { flatten(sources, query, alias, nestNextMap) }

            else -> trace("base| Nesting 'other' $query") andReturn { nest(source(query, alias)) }
          }
        }
        val type = finalFlatMapBody.type
        trace("Flattening (alias = $alias) sources $sources from $finalFlatMapBody") andReturn {
          with (finalFlatMapBody) {
            when {
              this is XR.ConcatMap ->
//        case ConcatMap(q, Ident(alias, _), p) =>
//          trace("Flattening| ConcatMap") andReturn {
//            FlattenSqlQuery(
//              from = source(q, alias) :: Nil,
//              select = selectValues(p).map(_.copy(concat = true))
//            )(quat)
//          }

                trace("Flattening| ConcatMap") andReturn {
                  FlattenSqlQuery(
                    from = sources + source(head, alias),
                    select = selectValues(body).map { it.copy(concat = true) },
                    type = type
                  )
                }

//        // Given a clause that looks like:
//        // people.groupByMap(p=>p.name)(a => (a.name,a.age.max))
//        // In the AST it's more like:
//        // GroupByMap(people,p=>p.name)(a:Person => p:(a.name,MAX(a.age)))
//        // more concretely:
//        // GroupBy(q:people,x:p,g:p.name)(a:Person, p:(a.name,MAX(a.age)))
//        case GroupByMap(q, x @ Ident(alias, _), g, a, p) =>
//          trace("Flattening| GroupByMap") andReturn {
//            val b = base(q, alias, nestNextMap = true)
//            // Same as ExpandSelection in Map(GroupBy)
//            val flatGroupByAsts = new ExpandSelection(b.from).ofSubselect(List(SelectValue(g))).map(_.ast)
//            val groupByClause =
//              if (flatGroupByAsts.length > 1) Tuple(flatGroupByAsts)
//              else flatGroupByAsts.head
//
//            // We need to change the `a` var in:
//            //   people.groupByMap(p=>p.name)(a => (a.name,a.age.max))
//            // to same alias as 1st clause:
//            //   p => (p.name,p.age.max)
//            // since these become select-clauses:
//            //   SelectValue(p.name,p.age.max)
//            // since the `p` variable is in the `from` part of the query
//            val realiasedSelect = BetaReduction(p, a -> x)
//            b.copy(groupBy = Some(groupByClause), select = this.selectValues(realiasedSelect))(quat)
//          }

//              this is XR.GroupByMap -> {
//                trace("Flattening| GroupByMap") andReturn {
//                  val b = base(head, alias = byAlias.name, nestNextMap = true)
//                  val flatGroupByAsts = ExpandSelection(b.from).ofSubselect(listOf(SelectValue(byBody))).map(_.ast)
//
//                }
//              }



//
//        case Map(q, Ident(alias, _), p) =>
//          val b = base(q, alias, nestNextMap = false)
//          val agg = b.select.collect { case s @ SelectValue(_: Aggregation, _, _) =>
//            s
//          }
//          if (!b.distinct.isDistinct && agg.isEmpty)
//            trace("Flattening| Map(Ident) [Simple]") andReturn
//              b.copy(select = selectValues(p))(quat)
//          else
//            trace("Flattening| Map(Ident) [Complex]") andReturn
//              FlattenSqlQuery(
//                from = QueryContext(apply(q), alias) :: Nil,
//                select = selectValues(p)
//              )(quat)
//
//        case Filter(q, Ident(alias, _), p) =>
//          // If it's a filter, pass on the value of nestNextMap in case there is a future map we need to nest
//          val b = base(q, alias, nestNextMap)
//          // If the filter body uses the filter alias, make sure it matches one of the aliases in the fromContexts
//          if (
//            b.where.isEmpty && (!CollectAst.byType[Ident](p).map(_.name).contains(alias) || collectAliases(b.from)
//              .contains(alias))
//          )
//            trace("Flattening| Filter(Ident) [Simple]") andReturn
//              b.copy(where = Some(p))(quat)
//          else
//            trace("Flattening| Filter(Ident) [Complex]") andReturn
//              FlattenSqlQuery(
//                from = QueryContext(apply(q), alias) :: Nil,
//                where = Some(p),
//                select = select(alias, quat)
//              )(quat)
//
//        case SortBy(q, Ident(alias, _), p, o) =>
//          val b        = base(q, alias, nestNextMap = false)
//          val criteria = orderByCriteria(p, o, b.from)
//          // If the sortBy body uses the filter alias, make sure it matches one of the aliases in the fromContexts
//          if (
//            b.orderBy.isEmpty && (!CollectAst.byType[Ident](p).map(_.name).contains(alias) || collectAliases(b.from)
//              .contains(alias))
//          )
//            trace("Flattening| SortBy(Ident) [Simple]") andReturn
//              b.copy(orderBy = criteria)(quat)
//          else
//            trace("Flattening| SortBy(Ident) [Complex]") andReturn
//              FlattenSqlQuery(
//                from = QueryContext(apply(q), alias) :: Nil,
//                orderBy = criteria,
//                select = select(alias, quat)
//              )(quat)
//
//        // TODO Finish describing
//        // Happens when you either have an aggregation in the middle of a query
//        // ...
//        // Or as the result of a map
//        case Aggregation(op, q: Query) =>
//          val b = flatten(q, alias)
//          b.select match {
//            case head :: Nil if !b.distinct.isDistinct =>
//              trace("Flattening| Aggregation(Query) [Simple]") andReturn
//                b.copy(select = List(head.copy(ast = Aggregation(op, head.ast))))(quat)
//            case other =>
//              trace("Flattening| Aggregation(Query) [Complex]") andReturn
//                FlattenSqlQuery(
//                  from = QueryContext(apply(q), alias) :: Nil,
//                  select = List(
//                    SelectValue(Aggregation(op, Ident("*", quat)))
//                  ) // Quat of a * aggregation is same as for the entire query
//                )(quat)
//          }
//
//        case agg @ Aggregation(_, _) =>
//          trace("Flattening| Aggregation(Invalid)") andReturn {
//            fail(
//              s"Found the aggregation `${agg}` in an invalid place. An SQL aggregation (e.g. min/max/etc...) cannot be used in the body of an SQL statement e.g. in the WHERE clause."
//            )
//          }
//
//        case Take(q, n) =>
//          val b = base(q, alias, nestNextMap = false)
//          if (b.limit.isEmpty)
//            trace("Flattening| Take [Simple]") andReturn
//              b.copy(limit = Some(n))(quat)
//          else
//            trace("Flattening| Take [Complex]") andReturn
//              FlattenSqlQuery(
//                from = QueryContext(apply(q), alias) :: Nil,
//                limit = Some(n),
//                select = select(alias, quat)
//              )(quat)
//
//        case Drop(q, n) =>
//          val b = base(q, alias, nestNextMap = false)
//          if (b.offset.isEmpty && b.limit.isEmpty)
//            trace("Flattening| Drop [Simple]") andReturn
//              b.copy(offset = Some(n))(quat)
//          else
//            trace("Flattening| Drop [Complex]") andReturn
//              FlattenSqlQuery(
//                from = QueryContext(apply(q), alias) :: Nil,
//                offset = Some(n),
//                select = select(alias, quat)
//              )(quat)
//
//        case Distinct(q) =>
//          val b = base(q, alias, nestNextMap = false)
//          trace("Flattening| Distinct") andReturn
//            b.copy(distinct = DistinctKind.Distinct)(quat)
//
//        case DistinctOn(q, Ident(alias, _), fields) =>
//          val distinctList =
//            fields match {
//              case Tuple(values) => values
//              case other         => List(other)
//            }
//
//          q match {
//            // Ideally we don't need to make an extra sub-query for every single case of
//            // distinct-on but it only works when the parent AST is an entity. That's because DistinctOn
//            // selects from an alias of an outer clause. For example, query[Person].map(p => Name(p.firstName, p.lastName)).distinctOn(_.name)
//            // (Let's say Person(firstName, lastName, age), Name(first, last)) will turn into
//            // SELECT DISTINCT ON (p.name), p.firstName AS first, p.lastName AS last, p.age FROM Person
//            // This doesn't work because `name` in `p.name` doesn't exist yet. Therefore we have to nest this in a subquery:
//            // SELECT DISTINCT ON (p.name) FROM (SELECT p.firstName AS first, p.lastName AS last, p.age FROM Person p) AS p
//            // The only exception to this is if we are directly selecting from an entity:
//            // query[Person].distinctOn(_.firstName) which should be fine: SELECT (x.firstName), x.firstName, x.lastName, a.age FROM Person x
//            // since all the fields inside the (...) of the DISTINCT ON must be contained in the entity.
//            case _: Entity =>
//              val b = base(q, alias, nestNextMap = false)
//              b.copy(distinct = DistinctKind.DistinctOn(distinctList))(quat)
//            case _ =>
//              trace("Flattening| DistinctOn") andReturn
//                FlattenSqlQuery(
//                  from = QueryContext(apply(q), alias) :: Nil,
//                  select = select(alias, quat),
//                  distinct = DistinctKind.DistinctOn(distinctList)
//                )(quat)
//          }
//
//        case other =>
//          trace("Flattening| Other") andReturn
//            FlattenSqlQuery(from = sources :+ source(other, alias), select = select(alias, quat))(quat)


              else -> TODO()
            }
          }
        }



        /*

         */
        TODO()
      }


    return TODO()
  }

  private fun selectValues(ast: XR) = listOf(SelectValue(ast))

  private fun source(ast: XR, alias: String): FromContext =
    with (ast) {
      when (this) {
        is XR.Entity            -> TableContext(this, alias)
        is XR.Infix             -> InfixContext(this, alias)
        is XR.FlatJoin          -> FlatJoinContext(joinType, source(head, id.name), on)
        is XR.Nested            -> QueryContext(invoke(head), alias)
        else                    -> QueryContext(invoke(ast), alias)
      }
    }
}
