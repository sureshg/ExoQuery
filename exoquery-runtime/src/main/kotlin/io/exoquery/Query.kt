package io.exoquery

import io.exoquery.annotation.ExoInternal
import io.exoquery.select.InnerMost
import io.exoquery.select.SelectClause
import io.exoquery.select.program
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
data class DynamicBinds(val list: List<Pair<BID, RuntimeBindValue>>) {
  companion object {
    fun empty() = DynamicBinds(listOf())
  }

  fun allVals() = list.map { it.second }.filterIsInstance<RuntimeBindValue.SqlVariableIdent>().map { it.value }

  operator fun plus(other: DynamicBinds) = DynamicBinds(this.list + other.list)
  operator fun plus(other: Pair<BID, RuntimeBindValue>) = DynamicBinds(this.list + other)
  operator fun minus(other: DynamicBinds) = DynamicBinds(this.list - other.list)
  operator fun minus(bid: BID) = DynamicBinds(this.list.filter { it.first != bid })
  // Note: Might want to use a hash set of `bids` if the list gets big
  operator fun minus(bids: List<BID>) = DynamicBinds(this.list.filter { !bids.contains(it.first) })
}
// The contents of this constructed directly as Kotlin IR nodes with the expressions dynamically inside e.g.
// IrCall(IrConstructor(Sym("RuntimeBindValue.String"), listOf(IrString("Joe")))
sealed interface RuntimeBindValue {
  data class SqlVariableIdent(val value: kotlin.String): RuntimeBindValue
}


sealed interface Query<T> {
  val xr: XR.Query
  val binds: DynamicBinds

//  val map get() = MapClause<T>(xr)

  // Table<Person>().filter(name == "Joe")
  fun <R> filterBy(f: context(EnclosedExpression) (T).() -> R): Query<T> = error("The map expression of the Query was not inlined")

  // Table<Person>().map(name)
  fun <R> mapBy(f: context(EnclosedExpression) (T).() -> R): Query<T> = error("The map expression of the Query was not inlined")

  fun <R> map(f: context(EnclosedExpression) (SqlVariable<T>) -> R): Query<T> = error("The map expression of the Query was not inlined")
  // TODO Need to understand how this would be parsed if the function body had val-assignments
  fun <R> mapExpr(f: Lambda1Expression, binds: DynamicBinds): Query<R> =
    QueryContainer(XR.Map(this.xr, f.ident, f.xr.body), binds)


  // Search for every Ident (i.e. GetValue) that has @SqlVariable in it's type
  // and check its ExtensionReciever which should be of type EnclosedExpression
  // (i.e. how to get that?)

  // "Cannot use the value of the variable 'foo' outside of a Enclosed Expression context

  fun <R> flatMap(f: (SqlVariable<T>) -> Query<R>): Query<R> = error("needs to be replaced by compiler")
  // TODO Make the compiler plug-in a SqlVariable that it creates based on the variable name in f
  fun <R> flatMapInternal(ident: XR.Ident, body: Query<R>, binds: DynamicBinds): Query<R> =
    QueryContainer(XR.FlatMap(this.xr, ident, body.xr), binds)




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
  val q =
    program<Query<T>, SqlVariable<T>, SelectClause<T>>(
      machine = SelectClause<T>(markerId),
      f = block
    ) as Query<T>

  // TODO Need to change the innermost map into a flatMap
  //return q as Q
  val markedXR = InnerMost(markerId).findAndMark(q.xr)
  return QueryContainer<T>(markedXR, q.binds) as Q
}

//data class Table<T>(override val xt: XR): Query<T>