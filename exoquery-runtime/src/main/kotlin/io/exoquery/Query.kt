package io.exoquery

import io.exoquery.annotation.ExoInternal
import io.exoquery.select.InnerMost
import io.exoquery.select.SelectClause
import io.exoquery.select.program
import io.exoquery.norm.ReifyRuntimeIdents
import io.exoquery.norm.ReifyRuntimeQueries
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

context(EnclosedExpression) fun <T> param(value: T): T = error("Lifting... toto write this message")


interface SqlExpression<T> {
  val xr: XR.Expression
  val binds: DynamicBinds
}

fun <T> select(clause: context(EnclosedExpression) () -> T): SqlExpression<T> = error("The map expression of the Query was not inlined")
fun <T> selectExpr(body: XR, binds: DynamicBinds): SqlExpression<T> =
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

  context(EnclosedExpression) operator fun invoke(): T =
    throw IllegalStateException("meaningful error about how can't use a sql variable in a runtime context and it should be impossible anyway becuase its not an EnclosedExpression")
}


sealed interface Query<T> {
  // TODO mark ExoInternal
  val xr: XR.Query
  // TODO mark ExoInternal
  val binds: DynamicBinds

  fun withReifiedIdents(): Query<T> {
    val (reifiedXR, bindIds) = ReifyRuntimeIdents.ofQuery(binds, xr)
    return QueryContainer<T>(reifiedXR, binds - bindIds)
  }

  fun withReifiedSubQueries(): Query<T> {
    val (reifiedXR, idsAndQueries) = ReifyRuntimeQueries.ofQuery(binds, xr)
    val idsToRemove = idsAndQueries.map { it.first }
    val idsToAdd = idsAndQueries.map { it.second.binds.list }.flatten()
    return QueryContainer<T>(reifiedXR, (binds - idsToRemove) + idsToAdd)
  }

//  val map get() = MapClause<T>(xr)

  // Table<Person>().filter(name == "Joe")
  fun <R> filterBy(f: context(EnclosedExpression) (T).() -> R): Query<T> = error("The map expression of the Query was not inlined")

  // Table<Person>().map(name)
  fun <R> mapBy(f: context(EnclosedExpression) (T).() -> R): Query<R> = error("The map expression of the Query was not inlined")

  fun <R> map(f: context(EnclosedExpression) (T) -> R): Query<R> = error("The map expression of the Query was not inlined")
  fun <R> mapExpr(id: XR.Ident, body: XR, binds: DynamicBinds): Query<R> =
    QueryContainer<R>(XR.Map(this.xr, id, body as XR.Expression), binds).withReifiedSubQueries()

  fun <R> filter(f: context(EnclosedExpression) (T) -> R): Query<T> = error("The filter expression of the Query was not inlined")
  // TODO Need to understand how this would be parsed in the correlated subquery case
  fun <R> filterExpr(id: XR.Ident, body: XR, binds: DynamicBinds): Query<R> =
    QueryContainer<R>(XR.Filter(this.xr, id, body as XR.Expression), binds).withReifiedSubQueries()

  fun <R> sortedBy(f: context(EnclosedExpression) (T) -> R): Query<T> = error("The sort-by expression of the Query was not inlined")
  fun <R> sortedByExpr(id: XR.Ident, body: XR, binds: DynamicBinds): Query<R> =
    QueryContainer<R>(XR.SortBy(this.xr, id, body as XR.Expression, XR.Ordering.Asc), binds).withReifiedSubQueries()

  // TODO sortedByDescending, sortedByAscending, sortedByOrders { expr }(Asc, Desc, etc.... <- make a DSL for this)

  // Need this kind of API to enable quotation
  //fun take(i: context(EnclosedExpression) () -> Int): Query<T>
  //fun takeExpr(i: context(EnclosedExpression) () -> Int): Query<T>
  //fun drop(i: context(EnclosedExpression) () -> Int): Query<T>
  //fun dropExpr(i: context(EnclosedExpression) () -> Int): Query<T>

  // but this is much more natural
  fun take(i: Int): Query<T> = error("...")
  fun takeExpr(i: XR.Expression): Query<T> = error("...")
  fun drop(i: Int): Query<T> = error("...")
  fun dropExpr(i: XR.Expression): Query<T> = error("...")



  // Search for every Ident (i.e. GetValue) that has @SqlVariable in it's type
  // and check its ExtensionReciever which should be of type EnclosedExpression
  // (i.e. how to get that?)

  // "Cannot use the value of the variable 'foo' outside of a Enclosed Expression context

  fun <R> flatMap(f: (T) -> Query<R>): Query<R> = error("needs to be replaced by compiler")
  fun <R> flatMapExpr(id: XR.Ident, body: XR, binds: DynamicBinds): Query<R> =
    QueryContainer<R>(XR.FlatMap(this.xr, id, body as XR.Query), binds).withReifiedSubQueries()

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
public fun <T, Q: Query<T>> query(block: suspend SelectClause<T>.() -> SqlExpression<T>): Q {
  val markerId = UUID.randomUUID().toString()

  val q =
    program<Query<T>, SqlExpression<T>, SelectClause<T>>(
      machine = SelectClause<T>(markerId),
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
  return queryContainerRaw.withReifiedSubQueries().withReifiedIdents() as Q
}
