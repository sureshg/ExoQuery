package io.exoquery

import io.exoquery.annotation.*
import io.exoquery.select.InnerMost
import io.exoquery.select.QueryClause
import io.exoquery.select.program
import io.exoquery.norm.ReifyRuntimes
import io.exoquery.printing.exoPrint
import io.exoquery.util.head
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import java.util.*

fun <T> getSqlVar(name: String): T =
  throw IllegalArgumentException("something meaningful")

sealed interface Expression {
  val xr: XR

  // Create an instance of this by doing Expression.lambda { (p: Person) -> p.name == "Joe" }
  // or Expression.lambda [{ (p: Person) -> p.name == "Joe" }] depending on the direction we'd like to go
  companion object {
    fun <T, R> lambda(f: (T) -> R): Lambda1Expression = TODO()
  }
}

data class Lambda1Expression(override val xr: XR.Function1): Expression {
  val ident get() = xr.param
}

data class EntityExpression(override val xr: XR.Entity): Expression

// TODO make this an inline class?
//data class MapClause<T>(val xr: XR) {
//  operator fun <R> get(f: (T) -> R): Query<R> = error("The map expression of the Query was not inlined")
//  fun <R> fromExpr(f: Lambda1Expression): Query<R> = QueryContainer(XR.Map(this.xr, f.ident, f.xr))
//}

// TODO find a way to make this private so user cannot grab it since only supposed to be used in query functions
class EnclosedExpression
fun enclosedExpressionError(msg: String = ""): Nothing {
  val output = "Enclosed Expression should not exist outside of a query function" + if (msg.isNotEmpty()) " $msg" else ""
  throw IllegalStateException(output)
}

context(EnclosedExpression) fun <T> param(value: T): T = error("Lifting... toto write this message")

interface SqlExpression<T>: ContainerOfXR {
  override val xr: XR.Expression
  override val binds: DynamicBinds
}

fun <T> SqlExpression<T>.convertToQuery(): Query<T> = QueryContainer<T>(XR.QueryOf(xr), binds)
fun <T> Query<T>.convertToSqlExpression(): SqlExpression<T> = SqlExpressionContainer<T>(XR.ValueOf(xr), binds)

fun <T> select(clause: context(EnclosedExpression) () -> T): SqlExpression<T> = error("The map expression of the Query was not inlined")
fun <T> selectExpr(body: XR, binds: DynamicBinds, loc: XR.Location): SqlExpression<T> =
  SqlExpressionContainer<T>(body as XR.Expression, binds)

data class SqlExpressionContainer<T>(override val xr: XR.Expression, override val binds: DynamicBinds): SqlExpression<T>

class SqlVariable<T>(variableName: String /* don't want this to intersect with extension function properties*/): SqlExpression<T> {
  private val _variableName = variableName

  @ExoInternal
  override val xr = XR.Ident(_variableName, XRType.Generic, XR.Location.Synth)

  @ExoInternal
  override val binds = DynamicBinds.empty()

  @ExoInternal
  fun getVariableName() = _variableName

  companion object {
    fun <T> new(name: String) = SqlVariable<T>(name)
  }
}


context(EnclosedExpression) operator fun <T> SqlExpression<T>.invoke(): T =
  throw IllegalStateException("meaningful error about how can't use a sql variable in a runtime context and it should be impossible anyway becuase its not an EnclosedExpression")

fun <T> SqlExpression<T>.invokeHidden(): T =
  throw IllegalStateException("meaningful error about how can't use a sql variable in a runtime context and it should be impossible anyway becuase its not an EnclosedExpression")

// same as .invoke()
context(EnclosedExpression) fun <T> SqlExpression<T>.asValue(): T =
  throw IllegalStateException("meaningful error about how can't use a sql variable in a runtime context and it should be impossible anyway becuase its not an EnclosedExpression")

class GroupedQuery<T>(private val head: XR.Query, private val byAlias: XR.Ident, private val byBody: XR.Expression, private val mapBinds: DynamicBinds) {
  @LambdaMethodProducingXR("mapExpr")
  fun <R> map(f: context(EnclosedExpression) (T) -> R): Query<R> = error("The map expression of the Query was not inlined")
  // TODO move into an extension method
  fun <R> mapExpr(mapAlias: List<XR.Ident>, mapBody: XR, binds: DynamicBinds, loc: XR.Location): Query<R> =
    QueryContainer<R>(XR.GroupByMap(head, byAlias, byBody, mapAlias.head, mapBody as XR.Expression, loc), mapBinds + binds).withReifiedRuntimes()
}

interface ContainerOfXR {
  val xr: XR
  val binds: DynamicBinds
}

fun <T> Query<T>.withReifiedRuntimes(): Query<T> {
  // first recursively reify all the binds inside the query to take care of any binds-in-binds
  val reifiedBinds = DynamicBinds(binds.list.map { (id, value) ->
    id to (when (value) {
      is RuntimeBindValue.RuntimeQuery -> value.withReifiedRuntimes()
      is RuntimeBindValue.RuntimeExpression -> value.withReifiedRuntimes()
    })
  })
  //println("-------------- Query: Before Reification --------------\n" + this.show())
  val (reifiedXR, idsAndQueries) = ReifyRuntimes.ofQueryXR(reifiedBinds, xr)
  val idsToRemove = idsAndQueries.map { it.id }
  val idsToAdd = idsAndQueries.map { it.value.binds.list }.flatten()
  val output = QueryContainer<T>(reifiedXR, (binds - idsToRemove) + idsToAdd)
  //println("-------------- Query: After Reification --------------\n" + output.show())
  return output
}



fun <T> SqlExpression<T>.withReifiedRuntimes(): SqlExpression<T> {
  // first recursively reify all the binds inside the expression to take care of any binds-in-binds
  val reifiedBinds = DynamicBinds(binds.list.map { (id, value) ->
    id to (when (value) {
      is RuntimeBindValue.RuntimeQuery -> value.withReifiedRuntimes()
      is RuntimeBindValue.RuntimeExpression -> value.withReifiedRuntimes()
    })
  })
  //println("-------------- SqlExpression: Before Reification --------------\n" + this.show())
  val (reifiedXR, idsAndQueries) = ReifyRuntimes.ofExpressionXR(reifiedBinds, xr)
  val idsToRemove = idsAndQueries.map { it.id }
  val idsToAdd = idsAndQueries.map { it.value.binds.list }.flatten()
  val output = SqlExpressionContainer<T>(reifiedXR, (binds - idsToRemove) + idsToAdd)
  //println("-------------- SqlExpression: After Reification --------------\n" + output.show())
  return output
}

fun <T> Query<T>.show() = exoPrint(this)
fun <T> SqlExpression<T>.show() = exoPrint(this)


// TODO Tomorrow: Move out all __Expr methods into separate space and use ChangeReciever annotations to delegate to call them
//      then check that Query<T>.___autocomplete___ is only the non __Expr methods. Then need to do similar things on Table<T>
//      the latter may involve copying some methods.
sealed interface Query<T>: ContainerOfXR {
  // TODO mark ExoInternal (may need to mark a bunch of other stuff with it as well)
  override val xr: XR.Query
  // TODO mark ExoInternal (may need to mark a bunch of other stuff with it as well)
  override val binds: DynamicBinds

//  val map get() = MapClause<T>(xr)

  // Table<Person>().filter(name == "Joe")
  //fun <R> filterBy(f: context(EnclosedExpression) (T).() -> R): Query<T> = error("The map expression of the Query was not inlined")

  // Table<Person>().map(name)
  //fun <R> mapBy(f: context(EnclosedExpression) (T).() -> R): Query<R> = error("The map expression of the Query was not inlined")

  @LambdaMethodProducingXR("mapExpr")
  fun <R> map(f: context(EnclosedExpression) (T) -> R): Query<R> = error("The map expression of the Query was not inlined")
  fun <R> mapExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<R> =
    QueryContainer<R>(XR.Map(this.xr, id.first(), body as XR.Expression, loc), binds).withReifiedRuntimes()

  @LambdaMethodProducingXR("groupByExpr")
  fun <R> groupBy(f: context(EnclosedExpression) (T) -> R): GroupedQuery<R> =  error("The groupBy expression of the Query was not inlined")
  fun <R> groupByExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): GroupedQuery<R> =
    GroupedQuery<R>(this.xr, id.head, body as XR.Expression, binds)

  @LambdaMethodProducingXR("filterExpr")
  fun <R> filter(f: context(EnclosedExpression) (T) -> R): Query<T> = error("The filter expression of the Query was not inlined")
  // TODO Need to understand how this would be parsed in the correlated subquery case
  fun <R> filterExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<R> =
    QueryContainer<R>(XR.Filter(this.xr, id.first(), body as XR.Expression, loc), binds).withReifiedRuntimes()

  @MethodProducingXR("unionExpr")
  fun <T> union(other: Query<T>): Query<T> = error("The union expression of the Query was not inlined")
  // In this case `other` is passed through directly. This is because the `other` query is already a QueryContainer
  // also, it shouldn't be possible to have any binds from this expression (since nothing is being parsed) but add them anyway
  // if for some odd reason they are introduced.
  fun <T> unionExpr(other: Query<T>, binds: DynamicBinds, loc: XR.Location): Query<T> =
    QueryContainer<T>(XR.Union(this.xr, other.xr, loc), this.binds + other.binds + binds).withReifiedRuntimes()

  @MethodProducingXR("unionAllExpr")
  fun <T> unionAll(other: Query<T>): Query<T> = error("The union-all expression of the Query was not inlined")
  fun <T> unionAllExpr(other: Query<T>, binds: DynamicBinds, loc: XR.Location): Query<T> =
    QueryContainer<T>(XR.UnionAll(this.xr, other.xr, loc), this.binds + other.binds + binds).withReifiedRuntimes()

  /** @see distinctExpr */
  @MethodProducingXR("distinctExpr")
  fun distinct(): Query<T> = error("The sort-by expression of the Query was not inlined")
  fun distinctExpr(binds: DynamicBinds, loc: XR.Location): Query<T> =
    QueryContainer<T>(XR.Distinct(this.xr, loc), binds).withReifiedRuntimes()

  @LambdaMethodProducingXR("distinctExpr")
  fun <R> distinctBy(): Query<T> = error("The sort-by expression of the Query was not inlined")
  fun distinctExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<T> =
    QueryContainer<T>(XR.DistinctOn(this.xr, id.first(), body as XR.Expression, loc), binds).withReifiedRuntimes()

  @MethodProducingXR("distinctExpr")
  fun nested(): Query<T> = error("The sort-by expression of the Query was not inlined")
  fun nestedExpr(binds: DynamicBinds, loc: XR.Location): Query<T> =
    QueryContainer<T>(XR.Nested(this.xr, loc), binds).withReifiedRuntimes()

  @LambdaMethodProducingXR("sortedByExpr")
  fun <R> sortedBy(f: context(EnclosedExpression) (T) -> R): Query<T> = error("The sort-by expression of the Query was not inlined")
  fun <R> sortedByExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<R> =
    QueryContainer<R>(XR.SortBy(this.xr, id.first(), body as XR.Expression, XR.Ordering.Asc, loc), binds).withReifiedRuntimes()

  // TODO sortedByDescending, sortedByAscending, sortedByOrders { expr }(Asc, Desc, etc.... <- make a DSL for this)

  @MethodProducingXR("takeExpr")
  fun take(f: context(EnclosedExpression) () -> Int): Query<T> = error("The take expression of the Query was not inlined")
  fun takeExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<T> =
    QueryContainer<T>(XR.Take(this.xr, body as XR.Expression, loc), binds).withReifiedRuntimes()

  @MethodProducingXR("dropExpr")
  fun drop(f: context(EnclosedExpression) () -> Int): Query<T> = error("The take expression of the Query was not inlined")
  fun dropExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<T> =
    QueryContainer<T>(XR.Drop(this.xr, body as XR.Expression, loc), binds).withReifiedRuntimes()

  @MethodProducingXR("takeSimpleExpr")
  fun take(@ParseXR num: Int): Query<T> =  error("The take expression of the Query was not inlined")
  fun takeSimpleExpr(numRaw: XR, binds: DynamicBinds, loc: XR.Location): Query<T> =
    // NOTE: there can't be lifts here since there's no context(EnclosedExpression) but there could be other kinds of binds e.g. IdentOrigin Runtime Idents
    QueryContainer<T>(XR.Take(this.xr, numRaw as XR.Expression, loc), binds).withReifiedRuntimes()

  @MethodProducingXR("dropSimpleExpr")
  fun drop(@ParseXR num: Int): Query<T> =  error("The take expression of the Query was not inlined")
  fun dropSimpleExpr(numRaw: XR, binds: DynamicBinds, loc: XR.Location): Query<T> =
    // NOTE: there can't be lifts here since there's no context(EnclosedExpression) but there could be other kinds of binds e.g. IdentOrigin Runtime Idents
    QueryContainer<T>(XR.Drop(this.xr, numRaw as XR.Expression, loc), binds).withReifiedRuntimes()


  // Search for every Ident (i.e. GetValue) that has @SqlVariable in it's type
  // and check its ExtensionReciever which should be of type EnclosedExpression
  // (i.e. how to get that?)

  // "Cannot use the value of the variable 'foo' outside of a Enclosed Expression context

  @LambdaMethodProducingXR("flatMapExpr")
  fun <R> flatMap(f: context(EnclosedExpression) (T) -> Query<R>): Query<R> = error("needs to be replaced by compiler")
  fun <R> flatMapExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<R> =
    QueryContainer<R>(XR.FlatMap(this.xr, id.first(), body as XR.Query, loc), binds).withReifiedRuntimes()

  @LambdaMethodProducingXR("concatMapExpr")
  fun <R> concatMap(f: (T) -> Iterable<R>): Query<R> = error("needs to be replaced by compiler")
  fun <R> concatMapExpr(id: List<XR.Ident>, body: XR, binds: DynamicBinds, loc: XR.Location): Query<R> =
    QueryContainer<R>(XR.ConcatMap(this.xr, id.first(), body as XR.Expression, loc), binds).withReifiedRuntimes()
}

data class QueryContainer<T>(override val xr: XR.Query, override val binds: DynamicBinds): Query<T>

// TODO make this constructor private? Shuold have a TableQuery.fromExpr constructor
class Table<T> private constructor (override val xr: XR.Entity, override val binds: DynamicBinds): Query<T> {
  companion object {
    // TODO need to implement this in the plugin
    operator fun <T> invoke(): Table<T> = error("The TableQuery create-table expression was not inlined")
    fun <T> fromExpr(entity: EntityExpression) = Table<T>(entity.xr, DynamicBinds.empty())
  }
}

@Suppress("UNCHECKED_CAST")
public fun <T, Q: Query<T>> query(block: suspend QueryClause<T>.() -> SqlExpression<T>): Q {
  val markerId = UUID.randomUUID().toString()

  val q =
    program<Query<T>, SqlExpression<T>, QueryClause<T>>(
      machine = QueryClause<T>(markerId),
      f = block
    ) as Query<T>

  val markedXR = InnerMost(markerId).findAndMark(q.xr)
  val queryContainerRaw = QueryContainer<T>(markedXR, q.binds)
  /*
  IMPORTANT Only do the `withReifiedIdents()` here and not in previous clauses because the reification process removes the
  identifiers we've found from the DynamicBinds state. That means that if there are any other clauses in the SelectClause
  below that reuse those identifiers the binds could be lost. For example:
    query {
      val p = from(Table<Person>)
      val a = join(Table<Address>).on { owner == p.id (<- need to reifiy `p` here) }
      select { p (<- need to reifiy `p` here as well) to a }
    }
  That means that if we reifiy idents at the end of the join-clause, it will return a QueryContainer with an empty
  binds list and the `select` clause at the end (which knows nothing about binds since it is a generic quotation function)
  will never get the information about the fact that p needs to be reified.
   */
  return queryContainerRaw.withReifiedRuntimes() as Q
}


sealed interface SortOrder {
  object Asc: SortOrder
  object AscNullsFirst: SortOrder
  object AscNullsLast: SortOrder
  object Desc: SortOrder
  object DescNullsFirst: SortOrder
  object DescNullsLast: SortOrder
}