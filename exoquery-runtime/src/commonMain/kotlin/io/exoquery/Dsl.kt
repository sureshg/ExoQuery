package io.exoquery

import io.exoquery.annotation.Dsl
import io.exoquery.annotation.DslFunctionCall
import io.exoquery.xr.EncodingXR
import io.exoquery.xr.XR
import kotlinx.serialization.decodeFromHexString

fun unpackExpr(expr: String): XR.Expression =
  EncodingXR.protoBuf.decodeFromHexString<XR.Expression>(expr)

fun unpackQuery(query: String): XR.Query =
  EncodingXR.protoBuf.decodeFromHexString<XR.Query>(query)



// data class Person(val name: String, val age: Int)
// fun example() {
//   val v = capture {
//     Table<Person>().map { p -> p.age }
//   }
// }

class MissingCaptureError(val msg: String): IllegalStateException(msg)
fun errorCap(message: Any): Nothing = throw MissingCaptureError(message.toString())

// TODO When context recivers are finally implemented in KMP, make this have a context-reciver that allows `use` to be used, otherwise don't allow it
fun <T> captureValue(block: CapturedBlock.() -> T): @Captured SqlExpression<T> = errorCap("Compile time plugin did not transform the tree")
fun <T> capture(block: CapturedBlock.() -> SqlQuery<T>): @Captured SqlQuery<T> = errorCap("Compile time plugin did not transform the tree")




interface CapturedBlock {
  @Dsl fun <T> select(block: SelectClauseCapturedBlock.() -> T): SqlQuery<T> = errorCap("The `select` expression of the Query was not inlined")

  @Dsl fun <T> param(value: T): T = errorCap("Compile time plugin did not transform the tree")
  val <T> SqlExpression<T>.use: T get() = throw IllegalArgumentException("Cannot `use` an SqlExpression outside of a quoted context")

  // Extension recivers for SqlQuery<T>
  @Dsl fun <T, R> SqlQuery<T>.map(f: (T) -> R): SqlQuery<R> = errorCap("The `map` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.flatMap(f: (T) -> SqlQuery<R>): SqlQuery<R> = errorCap("The `flatMap` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.concatMap(f: (T) -> Iterable<R>): SqlQuery<R> = errorCap("The `concatMap` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.filter(f: (T) -> Boolean): SqlQuery<T> = errorCap("The `filter` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.union(other: SqlQuery<T>): SqlQuery<T> = errorCap("The `union` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.unionAll(other: SqlQuery<T>): SqlQuery<T> = errorCap("The `unionAll` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.distinct(): SqlQuery<T> = errorCap("The `distinct` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.distinctBy(f: (T) -> R): SqlQuery<T> = errorCap("The `distinctBy` expression of the Query was not inlined")

  @DslFunctionCall fun <T> SqlQuery<T>.isNotEmpty(): Boolean = errorCap("The `isNotEmpty` expression of the Query was not inlined")
  @DslFunctionCall fun <T> SqlQuery<T>.isEmpty(): Boolean = errorCap("The `isEmpty` expression of the Query was not inlined")

  @Dsl fun <T> SqlQuery<T>.nested(): SqlQuery<T> = errorCap("The `nested` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.sortedBy(f: (T) -> R): SqlQuery<T> = errorCap("The sort-by expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.sortedByDescending(f: (T) -> R): SqlQuery<T> = errorCap("The sort-by expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.take(f: Int): SqlQuery<T> = errorCap("The take expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.drop(f: Int): SqlQuery<T> = errorCap("The drop expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.size(): SqlQuery<Int> = errorCap("The size expression of the Query was not inlined")

  // Used in groupBy and various other places to convert query to an expression
  @Dsl fun <T> SqlQuery<T>.value(): SqlExpression<T> = errorCap("The `value` expression of the Query was not inlined")

  @Dsl fun <T> Table(): SqlQuery<T> = errorCap("The `Table<T>` constructor function was not inlined")
}

sealed interface Ord {
  @Dsl object Asc: Ord
  @Dsl object Desc: Ord
  @Dsl object AscNullsFirst: Ord
  @Dsl object DescNullsFirst: Ord
  @Dsl object AscNullsLast: Ord
  @Dsl object DescNullsLast: Ord
}

interface SelectClauseCapturedBlock: CapturedBlock {
  @Dsl fun <T> from(query: SqlQuery<T>): T = errorCap("The `from` expression of the Query was not inlined")
  @Dsl fun <T> join(onTable: SqlQuery<T>, condition: (T) -> Boolean): T = errorCap("The `join` expression of the Query was not inlined")
  @Dsl fun <T> joinLeft(onTable: SqlQuery<T>, condition: (T) -> Boolean): T? = errorCap("The `joinLeft` expression of the Query was not inlined")

  // TODO JoinFull ?

  // TODO play around with this variant in the future
  // fun <T?> joinRight(onTable: SqlQuery<T>, condition: (T?) -> Boolean): T? = error("The `joinRight` expression of the Query was not inlined")

  @Dsl fun where(condition: Boolean): Unit = errorCap("The `where` expression of the Query was not inlined")
  @Dsl fun where(condition: () -> Boolean): Unit = errorCap("The `where` expression of the Query was not inlined")
  @Dsl fun groupBy(vararg groupings: Any): Unit = errorCap("The `groupBy` expression of the Query was not inlined")
  @Dsl fun sortBy(vararg orderings: Pair<*, Ord>): Unit = errorCap("The `sortBy` expression of the Query was not inlined")
}

fun <T> select(block: SelectClauseCapturedBlock.() -> T): @Captured SqlQuery<T> = errorCap("The `select` expression of the Query was not inlined")

// TODO Dsl functions for grouping

// TODO play around with having multiple from-clauses


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
