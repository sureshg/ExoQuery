package io.exoquery.xr

import io.exoquery.BID
import io.exoquery.fansi.Attrs
import io.exoquery.fansi.Color
import io.exoquery.fansi.Str
import io.exoquery.pprint
import io.exoquery.pprint.*
import io.exoquery.printing.PrintXR
import io.exoquery.printing.format
import io.exoquery.xr.MirrorIdiom.token
import io.decomat.Matchable as Mat
import io.decomat.Component as Slot
import io.decomat.MiddleComponent as MSlot
import io.decomat.productComponentsOf as productOf
import io.decomat.HasProductClass as PC




/**
 * This is the core syntax tree there are essentially three primary concepts represented here:
 * 1. XR.Query - These are Entity, Map, FlatMap, etc... that represent the building blocks of the `from` clause
 *    of SQL statements.
 * 2. XR.Expression - These are values like Ident, Const, etc... as well as XR.Function (which are essentially lambdas
 *    that are invoked via FunctionApplly), operators and functions.
 * 3. Actions - These are Sql Insert, Delete, Update and possibly corresponding batch actions.
 *
 * This only exceptions to this rule are Infix and Marker blocks that are used as any of the above four.
 * Infix in particular is represented as so sql("any_${}_sql_${}_here").as[Query/Expression/Action]
 *
 * The other miscellaneous elements are Branch, Block, and Variable which are used in the `when` clause i.e. XR.When
 * The type Ordering is used in the `sortBy` clause i.e. XR.SortBy is technically not part of the XR but closely related
 * enough that it needs corresponding Lifters and transforms.
 *
 * The hardest thing for me to understand when I started work on Quill what kinds of IR/AST
 * elements can fit inside of what other kinds. This was particular problematic because
 * they read something like `FlatMap(a: Ast, b: Ident, c: Ast)` where both `a` and `c`
 * could be anything from an Ident("x") to a `ReturningAction`. This syntax tree
 * attempts to balance expressability with an approporiate of constraints via subtyping relations.
 */
sealed interface XR {
  // The primary types of XR are Query, Expression, Function, and Action
  // there are additional values that are useful for pattern matching in various situations
  object Labels {
    // Things that store their own XRType. Right now this is just an Ident but in the future
    // it will also be a lifted value.
    sealed interface Terminal: Expression, XR
    sealed interface Function: XR {
      val params: List<XR.Ident>
      val body: XR.Expression
    }
  }

  abstract val type: XRType

  fun show(pretty: Boolean = false): String {
    val code = with (MirrorIdiom) { this@XR.token }
    return if (pretty)
      format(code)
    else
      code
  }

  fun showRaw(color: Boolean = true): String {
    val str = PrintXR()(this)
    return if (color) str.toString() else str.plainText
  }



  sealed class JoinType {
    object Inner: JoinType() { override fun toString() = "Inner" }
    object Left: JoinType() { override fun toString() = "Left" }
  }

  sealed interface Expression: XR
  sealed interface Query: XR

  // *******************************************************************************************
  // ****************************************** Query ******************************************
  // *******************************************************************************************


  // TODO XRType needs to be Product
  @Mat
  data class Entity(@Slot val name: String, override val type: XRType.Product): Query, PC<Entity> {
    override val productComponents = productOf(this, name)
    companion object {}
  }

  @Mat
  data class Filter(@Slot val a: XR.Query, @MSlot val ident: XR.Ident, @Slot val b: XR.Expression): Query, PC<Filter> {
    override val productComponents = productOf(this, a, ident, b)
    override val type get() = a.type
    companion object {}
  }

  @Mat
  data class Map(@Slot val a: XR.Query, @MSlot val ident: XR.Ident, @Slot val b: XR.Expression): Query, PC<Map> {
    override val productComponents = productOf(this, a, ident, b)
    override val type get() = b.type
    companion object {}
  }

  @Mat
  data class ConcatMap(@Slot val a: XR.Query, @MSlot val ident: XR.Ident, @Slot val b: XR.Expression): Query, PC<ConcatMap> {
    override val productComponents = productOf(this, a, ident, b)
    override val type get() = b.type
    companion object {}
  }

  @Mat
  data class SortBy(@Slot val query: XR.Query, @MSlot val alias: XR.Ident, @Slot val criteria: XR.Expression, val ordering: XR.Ordering): Query, PC<SortBy> {
    override val productComponents = productOf(this, query, criteria, ordering)
    override val type get() = query.type
    companion object {}
  }

  // Ordering elements are technically not part of the XR but closely related
  sealed interface Ordering {
    data class TupleOrdering(val elems: List<Ordering>): Ordering
    sealed interface PropertyOrdering: Ordering
    object Asc: PropertyOrdering
    object Desc: PropertyOrdering
    object AscNullsFirst: PropertyOrdering
    object DescNullsFirst: PropertyOrdering
    object AscNullsLast: PropertyOrdering
    object DescNullsLast: PropertyOrdering
  }

  // Treat this as a 2-slot use mapBody for matching since by-body is usually the less-important one
  @Mat
  data class GroupByMap(@Slot val query: XR.Query, val byAlias: Ident, val byBody: XR.Expression, val mapAlias: Ident, @Slot val mapBody: XR.Expression): Query, PC<GroupByMap> {
    override val productComponents = productOf(this, query, mapBody)
    override val type get() = query.type
    companion object {}
  }

  @Mat
  data class Aggregation(val operator: AggregationOperator, @Slot val body: XR.Expression): Query, PC<Aggregation> {
    override val productComponents = productOf(this, body)
    override val type by lazy {
      when (operator) {
        AggregationOperator.`min` -> body.type
        AggregationOperator.`max` -> body.type
        AggregationOperator.`avg` -> XRType.Value
        AggregationOperator.`sum` -> XRType.Value
        AggregationOperator.`size` -> XRType.Value
      }
    }
    companion object {}
  }

  @Mat
  data class Take(@Slot val query: XR.Query, @Slot val num: XR.Expression): Query, PC<Take> {
    override val productComponents = productOf(this, query, num)
    override val type get() = query.type
    companion object {}
  }

  @Mat
  data class Drop(@Slot val query: XR.Query, @Slot val num: XR.Expression): Query, PC<Drop> {
    override val productComponents = productOf(this, query, num)
    override val type get() = query.type
    companion object {}
  }

  @Mat
  data class Union(@Slot val a: XR.Query, @Slot val b: XR.Query): Query, PC<Union> {
    override val productComponents = productOf(this, a, b)
    override val type get() = a.type
    companion object {}
  }

  @Mat
  data class UnionAll(@Slot val a: XR.Query, @Slot val b: XR.Query): Query, PC<UnionAll> {
    override val productComponents = productOf(this, a, b)
    override val type get() = a.type
    companion object {}
  }

  @Mat
  data class FlatMap(@Slot val a: XR.Query, @MSlot val ident: XR.Ident, @Slot val b: XR.Query): Query, PC<FlatMap> {
    override val productComponents = productOf(this, a, ident, b)
    override val type get() = b.type
    companion object {}
  }

  @Mat
  data class FlatJoin(val joinType: JoinType, @Slot val a: XR.Query, @MSlot val aliasA: XR.Ident, @Slot val on: XR.Expression): Query, PC<FlatJoin> {
    override val productComponents = productOf(this, a, aliasA, on)
    override val type get() = a.type
    companion object {}
  }

  @Mat
  data class Distinct(@Slot val query: XR.Query): Query, PC<Distinct> {
    override val productComponents = productOf(this, query)
    override val type get() = query.type
    companion object {}
  }

  @Mat
  data class DistinctOn(@Slot val query: XR.Query, @MSlot val alias: XR.Ident, @Slot val by: XR.Expression): Query, PC<DistinctOn> {
    override val productComponents = productOf(this, query, alias, by)
    override val type get() = query.type
    companion object {}
  }

  @Mat
  data class Nested(@Slot val query: XR.Query): XR.Query, PC<Nested> {
    override val productComponents = productOf(this, query)
    override val type get() = query.type
    companion object {}
  }

  // ************************************************************************************************
  // ****************************************** Infix ********************************************
  // ************************************************************************************************

  // data class Infix(@Slot val parts: List<String>, @Slot val params: List<XR>, val pure: Boolean, val transparent: Boolean): Query, Expression, Function, PC<Infix> {
  //   override val productComponents = productOf(this, params)
  //   override val type get() = XRType.Value
  //   companion object {}
  // }

  @Mat
  data class Marker(@Slot val name: String, val expr: XR.Expression?): Query, Expression, PC<Marker> {
    override val productComponents = productOf(this, name)
    override val type get() = XRType.Generic
    companion object {}
  }


  // ************************************************************************************************
  // ****************************************** Function ********************************************
  // ************************************************************************************************

  // Functions are essentially lambdas that are invoked via FunctionApply. Since they can be written into
  // expresison-variables (inside of Encode-Expressions) etc... so they need to be subtypes of XR.Expression.

  @Mat
  data class Function1(val param: XR.Ident, @Slot override val body: XR.Expression): Expression, Labels.Function, PC<Function1> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}

    override val params get() = listOf(param)
  }

  @Mat
  data class FunctionN(override val params: List<Ident>, @Slot override val body: XR.Expression): Expression, Labels.Function, PC<FunctionN> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}
  }

  // ************************************************************************************************
  // ****************************************** Expression ******************************************
  // ************************************************************************************************

  /**
   * Note that the `function` slot can be an expression but in practice its almost always a function
   */
  @Mat
  data class FunctionApply(@Slot val function: Expression, @Slot val args: List<XR.Expression>): Expression, PC<FunctionApply> {
    override val productComponents = productOf(this, function, args)
    override val type get() = function.type
    companion object {}
  }

  @Mat
  data class BinaryOp(@Slot val a: XR, val op: BinaryOperator, @Slot val b: XR.Expression) : Expression, PC<BinaryOp> {
    override val productComponents = productOf(this, a, b)
    override val type by lazy {
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    }
    companion object {}
  }

  @Mat
  data class UnaryOp(val op: UnaryOperator, @Slot val expr: XR.Expression) : Expression, PC<UnaryOp> {
    override val productComponents = productOf(this, expr)
    override val type by lazy {
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    }
    companion object {}
  }

  // **********************************************************************************************
  // ****************************************** Terminal ******************************************
  // **********************************************************************************************

  @Mat
  data class IdentOrigin(@Slot val runtimeName: BID, val name: String, override val type: XRType, val visibility: Visibility = Visibility.Visible) : XR, Labels.Terminal, PC<IdentOrigin> {

    override val productComponents = productOf(this, runtimeName)
    companion object {}

    data class Id(val name: String)
    private val id = Id(runtimeName.value)

    override fun hashCode() = id.hashCode()
    override fun equals(other: Any?) = other is IdentOrigin && other.id == id
  }

  @Mat
  data class Ident(@Slot val name: String, override val type: XRType, val visibility: Visibility = Visibility.Visible) : XR, Labels.Terminal, PC<XR.Ident> {

    override val productComponents = productOf(this, name)
    companion object {}

    data class Id(val name: String)
    private val id = Id(name)

    override fun hashCode() = id.hashCode()
    override fun equals(other: Any?) = other is XR.Ident && other.id == id
  }

  sealed class Const: Expression {
    override val type = XRType.Value

    data class Boolean(val value: kotlin.Boolean) : Const()
    data class Char(val value: kotlin.Char) : Const()
    data class Byte(val value: kotlin.Int) : Const()
    data class Short(val value: kotlin.Short) : Const()
    data class Int(val value: kotlin.Int) : Const()
    data class Long(val value: kotlin.Long) : Const()
    data class String(val value: kotlin.String) : Const()
    data class Float(val value: kotlin.Float) : Const()
    data class Double(val value: kotlin.Double) : Const()
    object Null: Const() {
      override fun toString(): kotlin.String = "Null"
    }
  }

  @Mat
  data class Product(val name: String, @Slot val fields: List<Pair<String, XR.Expression>>): Expression, PC<Product> {
    override val productComponents = productOf(this, fields)
    override val type by lazy { XRType.Product(name, fields.map { it.first to it.second.type }) }
    companion object {}

    data class Id(val fields : List<Pair<String, XR.Expression>>)
    private val id = Id(fields)
    override fun hashCode() = id.hashCode()
    override fun equals(other: Any?) = other is Product && other.id == id
  }

  sealed interface Visibility {
    object Hidden: Visibility { override fun toString() = "Hidden" }
    object Visible: Visibility { override fun toString() = "Visible" }
  }

  @Mat
  data class Property(@Slot val of: XR.Expression, @Slot val name: String, val visibility: Visibility = Visibility.Visible) : XR.Expression, PC<Property> {
    override val productComponents = productOf(this, of, name)
    override val type: XRType by lazy {
      when (val tpe = of.type) {
        is XRType.Product -> tpe.getField(name) ?: XRType.Unknown
        else -> XRType.Unknown
      }
    }

    data class Id(val of: XR.Expression, val name: String)
    private val id = Id(of, name)
    override fun hashCode() = id.hashCode()
    override fun equals(other: Any?) = other is Property && other.id == id

    companion object {}
  }

  @Mat
  data class Block(@Slot val stmts: List<Variable>, @Slot val output: XR.Expression) : XR.Expression, PC<Block> {
    override val productComponents = productOf(this, stmts, output)
    override val type: XRType by lazy { output.type }
    companion object {}
  }

  @Mat
  data class When(@Slot val branches: List<Branch>, @Slot val orElse: XR.Expression) : Expression, PC<When> {
    override val productComponents = productOf(this, branches, orElse)
    override val type: XRType by lazy { branches.lastOrNull()?.type ?: XRType.Unknown }
    companion object {}
  }

  @Mat
  data class Branch(@Slot val cond: XR.Expression, @Slot val then: XR.Expression) : XR, PC<Branch> {
    override val productComponents = productOf(this, cond, then)
    override val type: XRType by lazy { then.type }
    companion object {}
  }

  @Mat
  data class Variable(@Slot val name: XR.Ident, @Slot val rhs: XR.Expression): XR, PC<Variable> {
    override val productComponents = productOf(this, name, rhs)
    override val type: XRType by lazy { rhs.type }
    companion object {}
  }
}

fun XR.isBottomTypedTerminal() =
  this is XR.Labels.Terminal && (this.type is XRType.Null || this.type is XRType.Generic || this.type is XRType.Unknown)

fun XR.isTerminal() =
  this is XR.Labels.Terminal

fun XR.Labels.Terminal.withType(type: XRType): XR.Expression =
  when (this) {
    is XR.Ident -> XR.Ident(name, type)
    is XR.IdentOrigin -> XR.IdentOrigin(runtimeName, name, type)
  }