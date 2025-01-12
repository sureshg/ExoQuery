package io.exoquery

import io.exoquery.annotation.Captured
import io.exoquery.xr.XR
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.protobuf.ProtoBuf

fun unpackExpr(expr: String): XR.Expression =
  ProtoBuf.decodeFromHexString<XR.Expression>(expr)

fun unpackQuery(query: String): XR.Query =
  ProtoBuf.decodeFromHexString<XR.Query>(query)



// data class Person(val name: String, val age: Int)
// fun example() {
//   val v = capture {
//     Table<Person>().map { p -> p.age }
//   }
// }

// TODO When context recivers are finally implemented in KMP, make this have a context-reciver that allows `use` to be used, otherwise don't allow it
fun <T> captureValue(block: CapturedBlock.() -> T): @Captured("initial-value") SqlExpression<T> = error("Compile time plugin did not transform the tree")
fun <T> capture(block: CapturedBlock.() -> SqlQuery<T>): @Captured("initial-value") SqlQuery<T> = error("Compile time plugin did not transform the tree")




interface CapturedBlock {
  fun <T> param(value: T): T = error("Compile time plugin did not transform the tree")
}

fun <T> Table(): SqlQuery<T> = error("The `Table<T>` constructor function was not inlined")

interface SelectClauseCapturedBlock: CapturedBlock {
  fun <T> from(query: SqlQuery<T>): T = error("The `from` expression of the Query was not inlined")
  fun <T> join(query: SqlQuery<T>): T = error("The `join` expression of the Query was not inlined")
  fun <T> joinLeft(onTable: SqlQuery<T>, condition: (T) -> Boolean): T? = error("The `joinLeft` expression of the Query was not inlined")

  // TODO JoinFull ?

  // TODO play around with this variant in the future
  // fun <T?> joinRight(onTable: SqlQuery<T>, condition: (T?) -> Boolean): T? = error("The `joinRight` expression of the Query was not inlined")

  fun where(condition: Boolean): Unit = error("The `where` expression of the Query was not inlined")
  fun groupBy(grouping: Any): Unit = error("The `groupBy` expression of the Query was not inlined")
  fun sortBy(sorting: Any): Unit = error("The `sortBy` expression of the Query was not inlined")
}

fun <T> select(block: SelectClauseCapturedBlock.() -> T): SqlQuery<T> = error("The `select` expression of the Query was not inlined")

// TODO Dsl functions for grouping

// The select-clause sub-AST
sealed interface SX {
  data class From(val variable: XR.Ident, val xr: XR.Query): SX
  sealed interface JoinClause: SX
  data class Join(val variable: XR.Ident, val xr: XR.Query, val condition: XR.Expression): JoinClause
  data class JoinLeft(val variable: XR.Ident, val xr: XR.Query, val condition: XR.Expression): JoinClause
  // TODO JoinFull when the DSL part is added
  data class Where(val condition: XR.Expression): SX
  data class GroupBy(val grouping: XR.Expression): SX
  data class SortBy(val sorting: XR.Expression): SX
}

// TODO play around with having multiple from-clauses
data class SXClause(val from: SX.From, val joins: List<SX.JoinClause>, val where: SX.Where?, val groupBy: SX.GroupBy?, val sortBy: SX.SortBy?)

//fun example() {
//  data class Person(val id: String, val name: String, val age: Int)
//  data class Address(val id: String, val personId: String, val street: String)
//  val myQuery: SqlQuery<Pair<Person, Address?>> =
//    select {
//      val p = from(Table<Person>())
//      val a = joinLeft(Table<Address>()) { it.personId == p.id } // note, when `it` is being used as a variable want to try to get it from the `val` part so it doesn't beta reduce to `it`
//      where(p.age > 18) // maybe `where { p.age > 18 }` would be better? Also need to think about multiple groupBy clauses, maybe we need tupleOf(...), possibly even directly in the signature (with overlods for pair/triple as well)
//      p to a
//    }
//}



