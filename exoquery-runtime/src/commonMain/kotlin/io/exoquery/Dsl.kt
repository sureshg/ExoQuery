package io.exoquery

import io.exoquery.annotation.Captured
import io.exoquery.kmp.pprint.PPrinter
import io.exoquery.pprint.PPrinterConfig
import io.exoquery.pprint.Tree
import io.exoquery.printing.PrintSkipLoc
import io.exoquery.xr.EncodingXR
import io.exoquery.xr.SelectClauseToXR
import io.exoquery.xr.StatefulTransformer
import io.exoquery.xr.StatelessTransformer
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.protobuf.ProtoBuf

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

// Unline XR, SX is not a recursive AST, is merely a prefix AST that has a common-base class
@Serializable
sealed interface SX {
  @Serializable
  data class From(val variable: XR.Ident, val xr: XR.Query, val loc: XR.Location = XR.Location.Synth): SX
  @Serializable
  data class Join(val joinType: XR.JoinType, val variable: XR.Ident, val onQuery: XR.Query, val conditionVariable: XR.Ident, val condition: XR.Expression, val loc: XR.Location = XR.Location.Synth): SX
  @Serializable
  data class Where(val condition: XR.Expression, val loc: XR.Location = XR.Location.Synth): SX
  @Serializable
  data class GroupBy(val grouping: XR.Expression, val loc: XR.Location = XR.Location.Synth): SX
  @Serializable
  data class SortBy(val sorting: XR.Expression, val ordering: XR.Ordering, val loc: XR.Location = XR.Location.Synth): SX
}

// The structure should be:
// val from: SX.From, val joins: List<SX.JoinClause>, val where: SX.Where?, val groupBy: SX.GroupBy?, val sortBy: SX.SortBy?
@Serializable
data class SelectClause(
  val from: List<SX.From>,
  val joins: List<SX.Join>,
  val where: SX.Where?,
  val groupBy: SX.GroupBy?,
  val sortBy: SX.SortBy?,
  val select: XR.Expression,
  override val type: XRType,
  override val loc: XR.Location = XR.Location.Synth
): XR.CustomQuery.Convertable {
  override fun toQueryXR(): XR.Query = SelectClauseToXR(this)

  // Do nothing for now, in the cuture recurse in queries and expressions inside the SX clauses
  override fun handleStatelessTransform(transformer: StatelessTransformer): XR.CustomQuery = this

  // Do nothing for now, in the cuture recurse in queries and expressions inside the SX clauses
  override fun <S> handleStatefulTransformer(transformer: StatefulTransformer<S>): Pair<XR.CustomQuery, StatefulTransformer<S>> = this to transformer
  override fun showTree(config: PPrinterConfig): Tree = PrintSkipLoc<SelectClause>(SelectClause.serializer(), config).treeify(this, null, false, false)

  companion object {
    fun justSelect(select: XR.Expression, loc: XR.Location): SelectClause = SelectClause(emptyList(), emptyList(), null, null, null, select, select.type, loc)
  }
}

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



