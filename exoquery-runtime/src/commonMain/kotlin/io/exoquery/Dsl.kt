package io.exoquery

import io.exoquery.Captured
import io.exoquery.annotation.Dsl
import io.exoquery.annotation.DslCall
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

// TODO When context recivers are finally implemented in KMP, make this have a context-reciver that allows `use` to be used, otherwise don't allow it
fun <T> captureValue(block: CapturedBlock.() -> T): @Captured SqlExpression<T> = error("Compile time plugin did not transform the tree")
fun <T> capture(block: CapturedBlock.() -> SqlQuery<T>): @Captured SqlQuery<T> = error("Compile time plugin did not transform the tree")




interface CapturedBlock {
  @Dsl fun <T> select(block: SelectClauseCapturedBlock.() -> T): SqlQuery<T> = error("The `select` expression of the Query was not inlined")

  @Dsl fun <T> param(value: T): T = error("Compile time plugin did not transform the tree")
  val <T> SqlExpression<T>.use: T get() = throw IllegalArgumentException("Cannot `use` an SqlExpression outside of a quoted context")

  // Extension recivers for SqlQuery<T>
  @Dsl fun <T, R> SqlQuery<T>.map(f: (T) -> R): SqlQuery<R> = error("The `map` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.flatMap(f: (T) -> SqlQuery<R>): SqlQuery<R> = error("The `flatMap` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.concatMap(f: (T) -> Iterable<R>): SqlQuery<R> = error("The `concatMap` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.filter(f: (T) -> Boolean): SqlQuery<T> = error("The `filter` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.union(other: SqlQuery<T>): SqlQuery<T> = error("The `union` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.unionAll(other: SqlQuery<T>): SqlQuery<T> = error("The `unionAll` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.distinct(): SqlQuery<T> = error("The `distinct` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.distinctBy(f: (T) -> R): SqlQuery<T> = error("The `distinctBy` expression of the Query was not inlined")

  @DslCall fun <T> SqlQuery<T>.isNotEmpty(): Boolean = error("The `isNotEmpty` expression of the Query was not inlined")
  @DslCall fun <T> SqlQuery<T>.isEmpty(): Boolean = error("The `isEmpty` expression of the Query was not inlined")

  @Dsl fun <T> SqlQuery<T>.nested(): SqlQuery<T> = error("The `nested` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.sortedBy(f: (T) -> R): SqlQuery<T> = error("The sort-by expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.sortedByDescending(f: (T) -> R): SqlQuery<T> = error("The sort-by expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.take(f: Int): SqlQuery<T> = error("The take expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.drop(f: Int): SqlQuery<T> = error("The drop expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.size(): SqlQuery<Int> = error("The size expression of the Query was not inlined")

  // Used in groupBy and various other places to convert query to an expression
  @Dsl fun <T> SqlQuery<T>.value(): SqlExpression<T> = error("The `value` expression of the Query was not inlined")
}

@Dsl fun <T> Table(): SqlQuery<T> = error("The `Table<T>` constructor function was not inlined")

sealed interface Ord {
  @Dsl object Asc: Ord
  @Dsl object Desc: Ord
  @Dsl object AscNullsFirst: Ord
  @Dsl object DescNullsFirst: Ord
  @Dsl object AscNullsLast: Ord
  @Dsl object DescNullsLast: Ord
}

interface SelectClauseCapturedBlock: CapturedBlock {
  @Dsl fun <T> from(query: SqlQuery<T>): T = error("The `from` expression of the Query was not inlined")
  @Dsl fun <T> join(onTable: SqlQuery<T>, condition: (T) -> Boolean): T = error("The `join` expression of the Query was not inlined")
  @Dsl fun <T> joinLeft(onTable: SqlQuery<T>, condition: (T) -> Boolean): T? = error("The `joinLeft` expression of the Query was not inlined")

  // TODO JoinFull ?

  // TODO play around with this variant in the future
  // fun <T?> joinRight(onTable: SqlQuery<T>, condition: (T?) -> Boolean): T? = error("The `joinRight` expression of the Query was not inlined")

  @Dsl fun where(condition: Boolean): Unit = error("The `where` expression of the Query was not inlined")
  @Dsl fun where(condition: () -> Boolean): Unit = error("The `where` expression of the Query was not inlined")
  @Dsl fun groupBy(vararg groupings: Any): Unit = error("The `groupBy` expression of the Query was not inlined")
  @Dsl fun sortBy(vararg orderings: Pair<*, Ord>): Unit = error("The `sortBy` expression of the Query was not inlined")
}

fun <T> select(block: SelectClauseCapturedBlock.() -> T): @Captured SqlQuery<T> = error("The `select` expression of the Query was not inlined")

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
