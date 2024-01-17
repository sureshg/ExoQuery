package io.exoquery.xr

import io.exoquery.xr.MirrorIdiom.token
import io.decomat.Matchable as Mat
import io.decomat.Component as Slot
import io.decomat.productComponentsOf as productOf
import io.decomat.HasProductClass as PC


/**
 * This is the core syntax tree there are essentially three primary concepts represented here:
 * 1. XR.Query - These are Entity, Map, FlatMap, etc... that represent the building blocks of the `from` clause
 *    of SQL statements.
 * 2. XR.Expression - These are values like Ident, Const, etc... as well as applied operators and functions.
 * 3. XR.Function - These are functions, more particular lambdas which have not been applied yet but can be via the
 *    FunctionApply construct.
 * 4. Actions - These are Sql Insert, Delete, Update and possibly corresponding batch actions.
 *
 * This only exceptions to this rule are Infix and Marker blocks that are used as any of the above four.
 * Infix in particular is represented as so sql("any_${}_sql_${}_here").as[Query/Expression/Function/Action]
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
  }

  abstract val type: XRType
//  override fun toString(): String =
//    with (MirrorIdiom) { this.token }

  sealed class JoinType {
    object Inner: JoinType()
    object Left: JoinType()
  }

  sealed interface Expression: XR
  sealed interface Query: XR
  sealed interface Function: XR {
    val params: List<XR.Ident>
    val body: XR.Expression
  }

  // *******************************************************************************************
  // ****************************************** Query ******************************************
  // *******************************************************************************************


  // TODO XRType needs to be Product
  @Mat
  data class Entity(@Slot val name: String, override val type: XRType): Query, PC<Entity> {
    override val productComponents = productOf(this, name)
    companion object {}
  }

  @Mat
  data class Filter(@Slot val a: XR.Query, val ident: Ident, @Slot val b: XR.Expression): Query, PC<Filter> {
    override val productComponents = productOf(this, a, b)
    override val type get() = a.type
    companion object {}
  }

  @Mat
  data class Map(@Slot val a: XR.Query, val ident: Ident, @Slot val b: XR.Expression): Query, PC<Map> {
    override val productComponents = productOf(this, a, b)
    override val type get() = b.type
    companion object {}
  }

  @Mat
  data class ConcatMap(@Slot val a: XR.Query, val ident: Ident, @Slot val b: XR.Expression): Query, PC<ConcatMap> {
    override val productComponents = productOf(this, a, b)
    override val type get() = b.type
    companion object {}
  }

  data class SortBy(@Slot val query: XR.Query, val alias: Ident, @Slot val criteria: XR.Expression, @Slot val ordering: XR.Ordering): Query, PC<SortBy> {
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

  data class GroupByMap(@Slot val query: XR.Query, val byAlias: Ident, @Slot val byBody: XR.Expression, val mapAlias: Ident, @Slot val mapBody: XR.Expression): Query, PC<GroupByMap> {
    override val productComponents = productOf(this, query, byBody, mapBody)
    override val type get() = query.type
    companion object {}
  }

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
  data class FlatMap(@Slot val a: XR.Query, val ident: Ident, @Slot val b: XR.Query): Query, PC<FlatMap> {
    override val productComponents = productOf(this, a, b)
    override val type get() = b.type
    companion object {}
  }

  @Mat
  data class FlatJoin(val joinType: JoinType, @Slot val a: XR.Query, val aliasA: Ident, @Slot val on: XR.Expression): Query, PC<FlatJoin> {
    override val productComponents = productOf(this, a, on)
    override val type get() = a.type
    companion object {}
  }

  data class Distinct(@Slot val query: XR.Query): Query, PC<Distinct> {
    override val productComponents = productOf(this, query)
    override val type get() = query.type
    companion object {}
  }

  data class DistinctOn(@Slot val query: XR.Query, val alias: Ident, @Slot val by: XR.Expression): Query, PC<DistinctOn> {
    override val productComponents = productOf(this, query, by)
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
  data class Marker(@Slot val name: String): Query, Expression, Function, PC<Marker> {
    override val productComponents = productOf(this, name)
    override val type get() = XRType.Generic
    companion object {}
    // Not useful, just needed to fulfil the contract of "Function"
    override val params get() = listOf<XR.Ident>()
    override val body: Expression = Ident(name, XRType.Generic)
  }


  // ************************************************************************************************
  // ****************************************** Function ********************************************
  // ************************************************************************************************
  @Mat
  data class Function1(val param: Ident, @Slot override val body: XR.Expression): Function, PC<Function1> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}

    override val params get() = listOf(param)
  }

  @Mat
  data class FunctionN(override val params: List<Ident>, @Slot override val body: XR.Expression): Function, PC<FunctionN> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}
  }

  // ************************************************************************************************
  // ****************************************** Expression ******************************************
  // ************************************************************************************************

  data class FunctionApply(@Slot val function: Function, @Slot val args: List<XR.Expression>): Expression, PC<FunctionApply> {
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
  data class Ident(@Slot val name: String, override val type: XRType) : XR, Labels.Terminal, PC<Ident> {
    override val productComponents = productOf(this, name)
    companion object {}

    data class Id(val name: String)
    private val id = Id(name)

    override fun hashCode() = id.hashCode()
    override fun equals(other: Any?) = other is Ident && other.id == id
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

  data class Product(val name: String, @Slot val fields: List<Pair<String, XR.Expression>>): Expression, PC<Product> {
    override val productComponents = productOf(this, fields)
    override val type by lazy { XRType.Product(name, fields.map { it.first to it.second.type }) }
    companion object {}

    data class Id(val fields : List<Pair<String, XR.Expression>>)
    private val id = Id(fields)
    override fun hashCode() = id.hashCode()
    override fun equals(other: Any?) = other is Product && other.id == id
  }

  // TODO Need to introduce Visibility into Property and Ident
  //      don't need Property.Renameable since we are assuming all renames will be done from case-class annotations
  //      before transformations begin.
  //      sealed interface Visibility {
  //        object Hidden: Visibility
  //        object Visible: Visibility
  //      }

  @Mat
  data class Property(@Slot val of: XR.Expression, @Slot val name: String) : XR.Expression, PC<Property> {
    override val productComponents = productOf(this, of, name)
    override val type: XRType by lazy {
      when (val tpe = of.type) {
        is XRType.Product -> tpe.getField(name) ?: XRType.Unknown
        else -> XRType.Unknown
      }
    }
    companion object {}
  }

  @Mat
  data class Block(@Slot val stmts: List<Variable>, @Slot val output: XR.Expression) : XR, PC<Block> {
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
  data class Variable(@Slot val name: Ident, @Slot val rhs: XR.Expression): XR, PC<Variable> {
    override val productComponents = productOf(this, name, rhs)
    override val type: XRType by lazy { rhs.type }
    companion object {}
  }
}

fun XR.isBottomTypedTerminal() =
  this is XR.Labels.Terminal && (this.type is XRType.Null || this.type is XRType.Generic || this.type is XRType.Unknown)

fun XR.isTerminal() =
  this is XR.Labels.Terminal

fun XR.Labels.Terminal.withType(type: XRType) =
  when (this) {
    is XR.Ident -> XR.Ident(name, type)
  }