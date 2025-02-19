package io.exoquery

import io.exoquery.annotation.Dsl
import io.exoquery.annotation.DslFunctionCall
import io.exoquery.annotation.DslFunctionCallType
import io.exoquery.annotation.DslNestingIgnore
import io.exoquery.annotation.ParamCtx
import io.exoquery.annotation.ParamCustom
import io.exoquery.annotation.ParamCustomValue
import io.exoquery.annotation.ParamStatic
import io.exoquery.serial.ParamSerializer
import io.exoquery.xr.EncodingXR
import io.exoquery.xr.XR
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.reflect.KClass

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

interface StringSqlDsl {
  @DslFunctionCall(DslFunctionCallType.PureFunction::class) fun left(i: Int): String = errorCap("The `left` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.PureFunction::class) fun right(i: Int): String = errorCap("The `right` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.PureFunction::class) fun replace(old: String, new: String): String = errorCap("The `replace` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.PureFunction::class) fun substring(start: Int, end: Int): String = errorCap("The `substring` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.PureFunction::class) fun uppercase(): String = errorCap("The `upperCase` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.PureFunction::class) fun lowercase(): String = errorCap("The `upperCase` expression of the Query was not inlined")
}



interface CapturedBlock {
  @Dsl fun <T> select(block: SelectClauseCapturedBlock.() -> T): SqlQuery<T> = errorCap("The `select` expression of the Query was not inlined")

  @Dsl @ParamStatic(ParamSerializer.String::class) fun param(value: String): String = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.Char::class) fun param(value: Char): Char = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.Int::class) fun param(value: Int): Int = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.Short::class) fun param(value: Short): Short = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.Long::class) fun param(value: Long): Long = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.Float::class) fun param(value: Float): Float = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.Double::class) fun param(value: Double): Double = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.Boolean::class) fun param(value: Boolean): Boolean = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.LocalDate::class) fun param(value: kotlinx.datetime.LocalDate): kotlinx.datetime.LocalDate = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.LocalTime::class) fun param(value: kotlinx.datetime.LocalTime): kotlinx.datetime.LocalTime = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamStatic(ParamSerializer.LocalDateTime::class) fun param(value: kotlinx.datetime.LocalDateTime): kotlinx.datetime.LocalDateTime = errorCap("Compile time plugin did not transform the tree")

  @Dsl @ParamCtx fun <T> paramCtx(value: T): T = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamCustom fun <T: Any> paramCustom(value: T, serializer: SerializationStrategy<T>): T = errorCap("Compile time plugin did not transform the tree")
  @Dsl @ParamCustomValue fun <T: Any> param(value: ValueWithSerializer<T>): T = errorCap("Compile time plugin did not transform the tree")

  // I.e. the the list is lifted but as individual elements
  //fun <T: Any> params(values: List<T>): Params<T> = errorCap("Compile time plugin did not transform the tree")
  //fun <T: Any> paramsCtx(values: List<T>): Params<T> = errorCap("Compile time plugin did not transform the tree")
  //fun <T: Any> paramsCustom(values: List<T>, serializer: SerializationStrategy<T>): Params<T> = errorCap("Compile time plugin did not transform the tree")
  //fun <T: Any> paramsCustomValue(values: List<ValueWithSerializer<T>>): Params<T> = errorCap("Compile time plugin did not transform the tree")
  //fun <T: Any> params(vararg values: T): Params<T> = errorCap("Compile time plugin did not transform the tree")
  //fun <T: Any> paramsCtx(vararg values: T): Params<T> = errorCap("Compile time plugin did not transform the tree")
  //// Maybe multiple args list?
  //fun <T: Any> paramsCustom(serializer: SerializationStrategy<T>, vararg values: T): Params<T> = errorCap("Compile time plugin did not transform the tree")

  val <T> SqlExpression<T>.use: T get() = throw IllegalArgumentException("Cannot `use` an SqlExpression outside of a quoted context")

  // Extension recivers for SqlQuery<T>
  @Dsl fun <T, R> SqlQuery<T>.map(f: (T) -> R): SqlQuery<R> = errorCap("The `map` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.flatMap(f: (T) -> SqlQuery<R>): SqlQuery<R> = errorCap("The `flatMap` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.concatMap(f: (T) -> Iterable<R>): SqlQuery<R> = errorCap("The `concatMap` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.filter(f: (T) -> Boolean): SqlQuery<T> = errorCap("The `filter` expression of the Query was not inlined")
  @Dsl infix fun <T> SqlQuery<T>.union(other: SqlQuery<T>): SqlQuery<T> = errorCap("The `union` expression of the Query was not inlined")
  @Dsl infix fun <T> SqlQuery<T>.unionAll(other: SqlQuery<T>): SqlQuery<T> = errorCap("The `unionAll` expression of the Query was not inlined")
  @Dsl operator fun <T> SqlQuery<T>.plus(other: SqlQuery<T>): SqlQuery<T> = errorCap("The `unionAll` expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.distinct(): SqlQuery<T> = errorCap("The `distinct` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.distinctBy(f: (T) -> R): SqlQuery<T> = errorCap("The `distinctBy` expression of the Query was not inlined")

  @DslNestingIgnore val String.sql @DslNestingIgnore get(): StringSqlDsl = errorCap("The `sql-dsl` expression of the Query was not inlined")

  // TODO get rid of Aggregation in XR in favor of below
  // TODO get rid of PostgisOps in operators in favor of MethodCall already implemented

  // TODO Need to test
  // Use this in the select or map clauses e.g. people.map(p -> min(p.age))
  @DslFunctionCall(DslFunctionCallType.Aggregator::class) fun <T: Comparable<T>> min(value: T): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.Aggregator::class) fun <T: Comparable<T>> max(value: T): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.Aggregator::class) fun <T: Comparable<T>> avg(value: T): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.Aggregator::class) fun <T: Comparable<T>> sum(value: T): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.Aggregator::class) fun <T> count(value: T): T = errorCap("The `min` expression of the Query was not inlined")

  // TODO Need to test
  // Use this as an aggregator for a query e.g. people.map(p -> p.age).min()
  // this is useful for co-releated subqueries e.g. events.filter(ev -> people.map(p -> p.age).avg() > ev.minAllowedAge) i.e. events to which the average person can come to
  @DslFunctionCall(DslFunctionCallType.QueryAggregator::class) fun <T: Comparable<T>> SqlQuery<T>.min(): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.QueryAggregator::class) fun <T: Comparable<T>> SqlQuery<T>.max(): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.QueryAggregator::class) fun <T: Comparable<T>> SqlQuery<T>.avg(): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.QueryAggregator::class) fun <T: Comparable<T>> SqlQuery<T>.sum(): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.QueryAggregator::class) fun <T> SqlQuery<T>.count(): T = errorCap("The `min` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.QueryAggregator::class) fun <T> SqlQuery<T>.isNotEmpty(): Boolean = errorCap("The `isNotEmpty` expression of the Query was not inlined")
  @DslFunctionCall(DslFunctionCallType.QueryAggregator::class) fun <T> SqlQuery<T>.isEmpty(): Boolean = errorCap("The `isEmpty` expression of the Query was not inlined")

  @Dsl fun <T> SqlQuery<T>.nested(): SqlQuery<T> = errorCap("The `nested` expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.sortedBy(f: (T) -> R): SqlQuery<T> = errorCap("The sort-by expression of the Query was not inlined")
  @Dsl fun <T, R> SqlQuery<T>.sortedByDescending(f: (T) -> R): SqlQuery<T> = errorCap("The sort-by expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.take(f: Int): SqlQuery<T> = errorCap("The take expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.drop(f: Int): SqlQuery<T> = errorCap("The drop expression of the Query was not inlined")
  @Dsl fun <T> SqlQuery<T>.size(): SqlQuery<Int> = errorCap("The size expression of the Query was not inlined")

  // Used in groupBy and various other places to convert query to an expression
  // e.g. events.filter(ev -> people.map(p -> customFunction(p.age)).value() > ev.minAllowedAge).value()
  // TODO Need to test
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

// TODO play around with having multiple from-clauses
fun <T> select(block: SelectClauseCapturedBlock.() -> T): @Captured SqlQuery<T> = errorCap("The `select` expression of the Query was not inlined")

interface ValueWithSerializer<T: Any> {
  val value: T
  val serializer: SerializationStrategy<T>
  val cls: KClass<T>

  fun asParam(): ParamSerializer<T> = ParamSerializer.Custom(serializer, cls)

  companion object {
    operator inline fun <reified T: Any> invoke(value: T, serializer: SerializationStrategy<T>): ValueWithSerializer<T> =
      ConcreteValueWithSerializer<T>(value, serializer, T::class)
  }
}

@PublishedApi
internal data class ConcreteValueWithSerializer<T: Any>(override val value: T, override val serializer: SerializationStrategy<T>, override val cls: KClass<T>): ValueWithSerializer<T>
