package io.exoquery

import io.exoquery.annotation.ExoInternal
import io.exoquery.select.InnerMost
import io.exoquery.select.SelectClause
import io.exoquery.select.program
import io.exoquery.norm.ReifyRuntimeIdents
import io.exoquery.norm.ReifyRuntimeQueries
import io.exoquery.xr.XR
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

// TODO Rename to SqlRow
class SqlVariable<T>(variableName: String /* don't want this to intersect with extension function properties*/) {
  private val _variableName = variableName

  @ExoInternal
  fun getVariableName() = _variableName

  companion object {
    fun <T> new(name: String) = SqlVariable<T>(name)
  }

  context(EnclosedExpression) operator fun invoke(): T =
    throw IllegalStateException("meaningful error about how can't use a sql variable in a runtime context and it should be impossible anyway becuase its not an EnclosedExpression")
}

// A runtime bind IDBindsAcc
data class BID(val value: String) {
  companion object {
    fun new() = BID(UUID.randomUUID().toString())
  }
}


sealed interface Query<T> {
  val xr: XR.Query
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

  fun reifyRuntimes() = this.withReifiedIdents().withReifiedSubQueries()

//  val map get() = MapClause<T>(xr)

  // Table<Person>().filter(name == "Joe")
  fun <R> filterBy(f: context(EnclosedExpression) (T).() -> R): Query<T> = error("The map expression of the Query was not inlined")

  // Table<Person>().map(name)
  fun <R> mapBy(f: context(EnclosedExpression) (T).() -> R): Query<R> = error("The map expression of the Query was not inlined")

  fun <R> map(f: context(EnclosedExpression) (T) -> R): Query<R> = error("The map expression of the Query was not inlined")
  fun <R> mapExpr(id: XR.Ident, body: XR, binds: DynamicBinds): Query<R> =
    QueryContainer<R>(XR.Map(this.xr, id, body as XR.Expression), binds).reifyRuntimes()

  fun <R> filter(f: context(EnclosedExpression) (T) -> R): Query<T> = error("The filter expression of the Query was not inlined")
  // TODO Need to understand how this would be parsed in the correlated subquery case
  fun <R> filterExpr(id: XR.Ident, body: XR, binds: DynamicBinds): Query<R> =
    QueryContainer<R>(XR.Filter(this.xr, id, body as XR.Expression), binds).reifyRuntimes()


  // Search for every Ident (i.e. GetValue) that has @SqlVariable in it's type
  // and check its ExtensionReciever which should be of type EnclosedExpression
  // (i.e. how to get that?)

  // "Cannot use the value of the variable 'foo' outside of a Enclosed Expression context

  fun <R> flatMap(f: (T) -> Query<R>): Query<R> = error("needs to be replaced by compiler")
  // TODO Make the compiler plug-in a SqlVariable that it creates based on the variable name in f
  fun <R> flatMapExpr(id: XR.Ident, body: XR, binds: DynamicBinds): Query<R> =
    QueryContainer<R>(XR.FlatMap(this.xr, id, body as XR.Query), binds).reifyRuntimes()




  //fun <R> flatMapExpr(f: Lambda1Expression): Query<R> =
  //  QueryContainer(XR.FlatMap(this.xr, f.ident, f.xr.body))

//  fun <T> join(source: Query<T>): OnClause<T> = OnClause(source)
}

//data class OnClause<T>(val source: Query<T>) {
//  fun on(predicate: (T) -> Boolean): Query<T> =  error("The join-on expression of the Query was not inlined")
//  fun onExpr(f: Lambda1Expression): Query<T> =  error("The join-on expression of the Query was not inlined")
//}

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
public fun <T, Q: Query<T>> select(block: suspend SelectClause<T>.() -> SqlVariable<T>): Q {
  /*
  public fun <Action, Result, T : ProgramBuilder<Action, Result>> program(
    machine: T,
    f: suspend T.() -> Result
): Action
   */
  val markerId = UUID.randomUUID().toString()

  var start = System.currentTimeMillis()
  val q =
    program<Query<T>, SqlVariable<T>, SelectClause<T>>(
      machine = SelectClause<T>(markerId),
      f = block
    ) as Query<T>
  println("--- Creating Program: ${(System.currentTimeMillis() - start).toDouble()/1000} ---")

  // TODO Need to change the innermost map into a flatMap
  //return q as Q

  start = System.currentTimeMillis()
  val markedXR = InnerMost(markerId).findAndMark(q.xr)
  println("--- Marking InnerMost: ${(System.currentTimeMillis() - start).toDouble()/1000} ---")

  return QueryContainer<T>(markedXR, q.binds) as Q
}

//data class Table<T>(override val xt: XR): Query<T>