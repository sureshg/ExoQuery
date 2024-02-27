@file:Suppress("NAME_SHADOWING", "NAME_SHADOWING")

package io.exoquery.sql

import io.exoquery.printing.PrintXR
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.*
import io.exoquery.xr.XR.FlatMap
import io.exoquery.xr.XR.Map
import io.exoquery.xr.XR.FlatJoin
import io.exoquery.xr.XR.Ordering.PropertyOrdering
import io.exoquery.xr.XR.Ordering.TupleOrdering
import io.exoquery.xrError
import io.decomat.Matchable as Mat
import io.decomat.Component as Slot
import io.decomat.productComponentsOf as productOf
import io.decomat.HasProductClass as PC

final data class OrderByCriteria(val ast: XR.Expression, val ordering: PropertyOrdering)

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

final data class FlatJoinContext(val joinType: XR.JoinType, val from: FromContext, val on: XR.Expression) : FromContext {
  override val type: XRType = from.type
}

sealed interface SqlQuery {
  val type: XRType

  fun showRaw(color: Boolean = true): String {
    val str = PrintXR()(this)
    return if (color) str.toString() else str.plainText
  }
}

sealed interface SetOperation
object UnionOperation : SetOperation
object UnionAllOperation : SetOperation

sealed interface DistinctKind {
  val isDistinct: Boolean

  object Distinct : DistinctKind { override val isDistinct: Boolean = true }
  data class DistinctOn(val props: List<XR.Expression>) : DistinctKind { override val isDistinct: Boolean = true }
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
  val query: SqlQuery,
  override val type: XRType
): SqlQuery


/**
 * Something that was fundemental about the Ast/XR that was not realized in Quill was that the fundemental differentiation
 * between SQL-based things is whether it is a Query-typed thing or an Expression-typed thing. Queries are things
 * that can go into From clauses and Join clause. Expressions are things that go into conditions and
 * into select clauses. When the AST becomes transformed to the point of being able to become an SqlQuery, the
 * job of the SqlQueryApply and related sets of functionality is to convert the Ast into the SqlQuery hiearchy.
 * Within this hiearchy, the SelectValue represents `Select x,y,z` clauses. These are things that
 * fundementally should be Expressions and not Queries. Indeed if a case is found where a SelectValue
 * is actually a Query, additional sub-type of SqlQuery shuold be created for it. Instead what should
 * be inside of each SelectValue is a Expression type of some kind whether it be a a Propery(id, name)
 * construct e.g. Property(Id(person), name) or a constant, or perhaps like an expression (a + b).
 * The only exception to this is an Infix which can both be a Query and Expression but in the case
 * of being inside a SelectValue, it should always be the latter.
 */
@Mat
final data class SelectValue(@Slot val expr: XR.Expression, @Slot val alias: String? = null, val concat: Boolean = false): PC<SelectValue> {
  override val productComponents = productOf(this, expr, alias)
  val type: XRType = expr.type
  companion object {}
}

final data class FlattenSqlQuery(
  val from: List<FromContext> = emptyList(),
  val where: XR.Expression? = null,
  val groupBy: XR.Expression? = null,
  val orderBy: List<OrderByCriteria> = emptyList(),
  val limit: XR.Expression? = null,
  val offset: XR.Expression? = null,
  val select: List<SelectValue> = emptyList(),
  val distinct: DistinctKind = DistinctKind.None,
  override val type: XRType
): SqlQuery

class SqlQueryApply(val traceConfig: TraceConfig) {
  val trace: Tracer = Tracer(TraceType.SqlQueryConstruct, traceConfig, 1)

  operator fun invoke(query: XR): SqlQuery =
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
          trace("Construct SqlQuery from: Query") andReturn { flatten(this, "x") }
        this is XR.Infix ->
          trace("Construct SqlQuery from: Infix") andReturn { flatten(this, "x") }
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

    fun select(alias: String, type: XRType): List<SelectValue> = listOf(SelectValue(XR.Ident(alias, type), null))

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
      }

      val type = finalFlatMapBody.type
      return trace("Flattening (alias = $alias) sources $sources from $finalFlatMapBody") andReturn {
        with (finalFlatMapBody) {
          when {
            this is XR.ConcatMap ->
              trace("Flattening| ConcatMap") andReturn {
                FlattenSqlQuery(
                  from = sources + source(head, alias),
                  select = selectValues(body).map { it.copy(concat = true) },
                  type = type
                )
              }

            this is XR.GroupByMap -> {
              trace("Flattening| GroupByMap") andReturn {
                val b = base(head, alias = byAlias.name, nestNextMap = true)
                val flatGroupByAsts = ExpandSelection(b.from).ofSubselect(listOf(SelectValue(byBody))).map { it.expr }
                val groupByClause: XR.Expression =
                  if (flatGroupByAsts.size > 1) XR.Product.TupleNumeric(flatGroupByAsts)
                  else flatGroupByAsts.first()

                // We need to change the `a` var in:
                //   people.groupByMap(p=>p.name)(a => (a.name,a.age.max))
                // to same alias as 1st clause:
                //   p => (p.name,p.age.max)
                // since these become select-clauses:
                //   SelectValue(p.name,p.age.max)
                // since the `p` variable is in the `from` part of the query
                val realiasedSelect = BetaReduction(byBody, byAlias to mapAlias)
                b.copy(groupBy = groupByClause, select = selectValues(realiasedSelect), type = type)
              }
            }

            this is Map -> {
              val b = base(head, alias, nestNextMap = false)
              val aggs = b.select.filter { it.expr is XR.Aggregation }
              if (!b.distinct.isDistinct && aggs.isEmpty())
                trace("Flattening| Map(Ident) [Simple]") andReturn {
                  b.copy(select = selectValues(body), type = type)
                }
              else
                trace("Flattening| Map(Ident) [Complex]") andReturn {
                  FlattenSqlQuery(
                    from = listOf(QueryContext(invoke(head), alias)),
                    select = selectValues(body),
                    type = type
                  )
                }
            }

            this is XR.Filter -> {
              val alias = id.name
              // If it's a filter, pass on the value of nestNextMap in case there is a future map we need to nest
              val b = base(head, alias, nestNextMap)
              // If the filter body uses the filter alias, make sure it matches one of the aliases in the fromContexts
              if (b.where == null && (!CollectXR.byType<XR.Ident>(body).map { it.name }
                  .contains(alias) || collectAliases(b.from).contains(alias)))
                trace("Flattening| Filter(Ident) [Simple]") andReturn {
                  b.copy(where = body, type = type)
                }
              else
                trace("Flattening| Filter(Ident) [Complex]") andReturn {
                  FlattenSqlQuery(
                    from = listOf(QueryContext(invoke(head), alias)),
                    where = body,
                    select = select(alias, type),
                    type = type
                  )
                }
            }

            this is XR.SortBy -> {
              fun allIdentsIn(criteria: List<OrderByCriteria>) =
                criteria.flatMap { CollectXR.byType<XR.Ident>(it.ast).map { it.name } }

              val alias = id.name
              val b = base(head, alias, nestNextMap = false)
              val criteria = orderByCriteria(criteria, ordering, b.from)
              // If the sortBy body uses the filter alias, make sure it matches one of the aliases in the fromContexts
              if (b.where == null && (!allIdentsIn(criteria).contains(alias) || collectAliases(b.from).contains(alias)))
                trace("Flattening| SortBy(Ident) [Simple]") andReturn {
                  b.copy(orderBy = criteria, type = type)
                }
              else
                trace("Flattening| SortBy(Ident) [Complex]") andReturn {
                  FlattenSqlQuery(
                    from = listOf(QueryContext(invoke(head), alias)),
                    orderBy = criteria,
                    select = select(alias, type),
                    type = type
                  )
                }
            }

            this is XR.Take -> {
              val b = base(head, alias, nestNextMap = false)
              if (b.limit == null)
                trace("Flattening| Take [Simple]") andReturn {
                  b.copy(limit = num, type = type)
                }
              else
                trace("Flattening| Take [Complex]") andReturn {
                  FlattenSqlQuery(
                    from = listOf(QueryContext(invoke(head), alias)),
                    limit = num,
                    select = select(alias, type),
                    type = type
                  )
                }
            }

            this is XR.Drop -> {
              val b = base(head, alias, nestNextMap = false)
              if (b.offset != null && b.limit != null)
                trace("Flattening| Drop [Simple]") andReturn {
                  b.copy(offset = num, type = type)
                }
              else
                trace("Flattening| Drop [Complex]") andReturn {
                  FlattenSqlQuery(
                    from = listOf(QueryContext(invoke(head), alias)),
                    offset = num,
                    select = select(alias, type),
                    type = type
                  )
                }
            }

            this is XR.Distinct -> {
              val b = base(head, alias, nestNextMap = false)
              trace("Flattening| Distinct") andReturn {
                b.copy(distinct = DistinctKind.Distinct, type = type)
              }
            }

            this is XR.DistinctOn -> {
              val distinctList =
                when (by) {
                  // Typically when you have something like `people.distinctOn(p -> tupleOf(p.name, p.age))`
                  // and the tuple-of is some kind of product-type
                  is XR.Product -> by.fields.map { it.second }
                  else -> listOf(by)
                }

              when (head) {
                // Ideally we don't need to make an extra sub-query for every single case of
                // distinct-on but it only works when the parent AST is an entity. That's because DistinctOn
                // selects from an alias of an outer clause. For example, query[Person].map(p => Name(p.firstName, p.lastName)).distinctOn(_.name)
                // (Let's say Person(firstName, lastName, age), Name(first, last)) will turn into
                // SELECT DISTINCT ON (p.name), p.firstName AS first, p.lastName AS last, p.age FROM Person
                // This doesn't work because `name` in `p.name` doesn't exist yet. Therefore we have to nest this in a subquery:
                // SELECT DISTINCT ON (p.name) FROM (SELECT p.firstName AS first, p.lastName AS last, p.age FROM Person p) AS p
                // The only exception to this is if we are directly selecting from an entity:
                // query[Person].distinctOn(_.firstName) which should be fine: SELECT (x.firstName), x.firstName, x.lastName, a.age FROM Person x
                // since all the fields inside the (...) of the DISTINCT ON must be contained in the entity.
                is XR.Entity -> {
                  val b = base(head, alias, nestNextMap = false)
                  b.copy(distinct = DistinctKind.DistinctOn(distinctList), type = type)
                }

                else ->
                  trace("Flattening| DistinctOn") andReturn {
                    FlattenSqlQuery(
                      from = listOf(QueryContext(invoke(head), alias)),
                      select = select(alias, type),
                      distinct = DistinctKind.DistinctOn(distinctList),
                      type = type
                    )
                  }
              }
            }

            else ->
              trace("Flattening| Other") andReturn {
                FlattenSqlQuery(
                  from = sources + source(this, alias),
                  select = select(alias, type),
                  type = type
                )
              }
          }
        }
      }
    }

  private fun orderByCriteria(ast: XR.Expression, ord: XR.Ordering, from: List<FromContext>): List<OrderByCriteria> =
    when {
      // Typically when you have something like `people.sortBy(p -> tupleOf(p.name, p.age))(tupleOf(Asc, Desc))`
      // the tuple of Asc/Descs needs to have the same size as the sortBy tuple
      ast is XR.Product && ord is TupleOrdering -> {
        if (ord.elems.size != ast.fields.size) xrError("TODO error msg")
        ord.elems.zip(ast.fields).flatMap { (ordElem, field) -> orderByCriteria(field.second, ordElem, from) }
      }
      // This is when you've got a single Asc/Desc and a tuple of sortBys e.g. `people.sortBy(p -> tupleOf(p.name, p.age))(Asc)`
      // in this case apply the Asc to each one recursively.
      ast is XR.Product && ord is PropertyOrdering ->
        ast.fields.flatMap { (_, value) -> orderByCriteria(value, ord, from) }

      // if its a quat product, use ExpandSelection to break it down into its component fields and apply the ordering to all of them
      // This is when you've got a single Asc/Desc and a ident in the sortBy e.g. `people.sortBy(p -> p)(Asc)`
      // that means we want to sort by every single field as Asc so apply the sorting recurisvely.
      ast is XR.Ident && ord is PropertyOrdering ->
        ExpandSelection(from).ofSubselect(listOf(SelectValue(ast))).map { it.expr }.flatMap { orderByCriteria(it, ord, from) }

      ord is PropertyOrdering -> listOf(OrderByCriteria(ast, ord))
      else -> xrError("Invalid order by criteria $ast")
    }

  private fun selectValues(ast: XR.Expression) = listOf(SelectValue(ast))

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

  private fun collectAliases(contexts: List<FromContext>): List<String> =
    contexts.flatMap {
      with (it) {
        when (this) {
          is TableContext             -> listOf(alias)
          is QueryContext             -> listOf(alias)
          is InfixContext             -> listOf(alias)
          is FlatJoinContext -> collectAliases(listOf(from))
        }
      }
    }

  private fun collectTableAliases(contexts: List<FromContext>): List<String> =
    contexts.flatMap {
      with (it) {
        when (this) {
          is TableContext             -> listOf(alias)
          is QueryContext             -> emptyList()
          is InfixContext             -> emptyList()
          is FlatJoinContext -> collectAliases(listOf(from))
        }
      }
    }
}
