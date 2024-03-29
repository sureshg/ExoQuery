package io.exoquery.xr

import io.decomat.ProductClass
import io.exoquery.BID
import io.exoquery.SortOrder
import io.exoquery.printing.PrintXR
import io.exoquery.printing.format
import io.exoquery.sql.SqlIdiom
import io.exoquery.util.dropLastSegment
import io.exoquery.util.takeLastSegment
import io.decomat.Matchable as Mat
import io.decomat.Component as Slot
import io.decomat.MiddleComponent as MSlot
import io.decomat.ConstructorComponent as CS
import io.decomat.productComponentsOf as productOf
import io.decomat.HasProductClass as PC


// TODO everything now needs to use Decomat Ids

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
    sealed interface FlatUnit: XR.Query
    sealed interface Function: XR {
      val params: List<XR.Ident>
      val body: XR.Expression
    }
  }

  abstract val type: XRType
  abstract val loc: XR.Location

  fun show(pretty: Boolean = false): String {
    val code = with (MirrorIdiom()) { this@XR.token }
    return if (pretty)
      format(code)
    else
      code
  }

  fun showRaw(color: Boolean = true): String {
    val str = PrintXR()(this)
    return if (color) str.toString() else str.plainText
  }

  fun translateWith(idiom: SqlIdiom) = idiom.translate(this)


  sealed class JoinType {
    object Inner: JoinType() { override fun toString() = "Inner" }
    object Left: JoinType() { override fun toString() = "Left" }
  }

  sealed interface Expression: XR
  sealed interface Query: XR

  // *******************************************************************************************
  // ****************************************** Query ******************************************
  // *******************************************************************************************

  data class RuntimeQueryBind(val id: BID, override val type: XRType, override val loc: Location = Location.Synth): Query

  // TODO XRType needs to be Product
  @Mat
  data class Entity(@Slot val name: String, override val type: XRType.Product, override val loc: Location = Location.Synth): Query, PC<Entity> {
    override val productComponents = productOf(this, name)
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Entity && other.id() == cid
  }

  @Mat
  data class Filter(@Slot val head: XR.Query, @MSlot val id: XR.Ident, @Slot val body: XR.Expression, override val loc: Location = Location.Synth): Query, PC<Filter> {
    override val productComponents = productOf(this, head, id, body)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Filter && other.id() == cid
  }

  @Mat
  data class Map(@Slot val head: XR.Query, @MSlot val id: XR.Ident, @Slot val body: XR.Expression, override val loc: Location = Location.Synth): Query, PC<Map> {
    override val productComponents = productOf(this, head, id, body)
    override val type get() = body.type
    companion object {
    }
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Map && other.id() == cid
  }

  @Mat
  data class ConcatMap(@Slot val head: XR.Query, @MSlot val id: XR.Ident, @Slot val body: XR.Expression, override val loc: Location = Location.Synth): Query, PC<ConcatMap> {
    override val productComponents = productOf(this, head, id, body)
    override val type get() = body.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is ConcatMap && other.id() == cid
  }

  data class FqName(val path: String, val name: String) {
    companion object {
      operator fun invoke(fullPath: String): FqName =
        FqName(fullPath.dropLastSegment(), fullPath.takeLastSegment())
    }

    override fun toString(): String = "$path.$name"
  }

  /**
   * This is the primary to way to turn a query into an expression both for things like aggregations
   * and co-related subqueries. For example an aggregation Query<Int>.avg in something like `people.map(_.age).avg`
   * should actually be represented as `people.map(_.age).map(i -> sum(i)).value` whose tree is:
   * `ValueOf(Map(Map(people, x, x.age), i, sum(i)))`. In situations where GlobalCall/MethodCall are used perhaps
   * we shold use this as well to convert to expressions. To fully support that we might need to have
   * the reverse of ValueOf that would convert a XR.Expression back into an XR.Query.
   */
  @Mat
  data class ValueOf(@Slot val head: XR.Query, override val loc: Location = Location.Synth): Expression, PC<ValueOf> {
    override val productComponents = productOf(this, head)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is ValueOf && other.id() == cid
  }

  @Mat
  data class SortBy(@Slot val head: XR.Query, @MSlot val id: XR.Ident, @Slot val criteria: XR.Expression, @CS val ordering: XR.Ordering, override val loc: Location = Location.Synth): Query, PC<SortBy> {
    override val productComponents = productOf(this, head, criteria, ordering)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is SortBy && other.id() == cid
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

    companion object {
      fun fromDslOrdering(orders: List<SortOrder>): XR.Ordering =
        if (orders.size == 1) {
          when (orders.first()) {
            SortOrder.Asc -> Ordering.Asc
            SortOrder.AscNullsFirst -> Ordering.AscNullsFirst
            SortOrder.AscNullsLast -> Ordering.AscNullsLast
            SortOrder.Desc -> Ordering.Desc
            SortOrder.DescNullsFirst -> Ordering.DescNullsFirst
            SortOrder.DescNullsLast -> Ordering.DescNullsLast
          }
        } else {
          // Repeat for N size=1 orders each one of which should give a single XR.Ordering
          Ordering.TupleOrdering(orders.map { fromDslOrdering(listOf(it)) })
        }
    }
  }

  // Treat this as a 2-slot use mapBody for matching since by-body is usually the less-important one
  @Mat
  data class GroupByMap(@Slot val head: XR.Query, @CS val byAlias: Ident, @CS val byBody: XR.Expression, @CS val mapAlias: Ident, @Slot val mapBody: XR.Expression, override val loc: Location = Location.Synth): Query, PC<GroupByMap> {
    override val productComponents = productOf(this, head, mapBody)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is GroupByMap && other.id() == cid
  }

  @Mat
  data class Take(@Slot val head: XR.Query, @Slot val num: XR.Expression, override val loc: Location = Location.Synth): Query, PC<Take> {
    override val productComponents = productOf(this, head, num)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Take && other.id() == cid
  }

  @Mat
  data class Drop(@Slot val head: XR.Query, @Slot val num: XR.Expression, override val loc: Location = Location.Synth): Query, PC<Drop> {
    override val productComponents = productOf(this, head, num)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Drop && other.id() == cid
  }

  @Mat
  data class Union(@Slot val a: XR.Query, @Slot val b: XR.Query, override val loc: Location = Location.Synth): Query, PC<Union> {
    override val productComponents = productOf(this, a, b)
    override val type get() = a.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Union && other.id() == cid
  }

  @Mat
  data class UnionAll(@Slot val a: XR.Query, @Slot val b: XR.Query, override val loc: Location = Location.Synth): Query, PC<UnionAll> {
    override val productComponents = productOf(this, a, b)
    override val type get() = a.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is UnionAll && other.id() == cid
  }

  @Mat
  data class FlatMap(@Slot val head: XR.Query, @MSlot val id: XR.Ident, @Slot val body: XR.Query, override val loc: Location = Location.Synth): Query, PC<FlatMap> {
    override val productComponents = productOf(this, head, id, body)
    override val type get() = body.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is FlatMap && other.id() == cid
  }

  @Mat
  data class FlatJoin(val joinType: JoinType, @Slot val head: XR.Query, @MSlot val id: XR.Ident, @Slot val on: XR.Expression, override val loc: Location = Location.Synth): Query, PC<FlatJoin> {
    override val productComponents = productOf(this, head, id, on)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is FlatJoin && other.id() == cid
  }

  @Mat
  data class FlatGroupBy(@Slot val by: XR.Expression, override val loc: Location = Location.Synth): Query, Labels.FlatUnit, PC<FlatGroupBy> {
    override val productComponents = productOf(this, by)
    override val type get() = by.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is FlatGroupBy && other.id() == cid
  }

  @Mat
  data class FlatSortBy(@Slot val by: XR.Expression, @CS val ordering: XR.Ordering, override val loc: Location = Location.Synth): Query, Labels.FlatUnit, PC<FlatSortBy> {
    override val productComponents = productOf(this, by)
    override val type get() = by.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is FlatSortBy && other.id() == cid
  }

  @Mat
  data class FlatFilter(@Slot val by: XR.Expression, override val loc: Location = Location.Synth): Query, Labels.FlatUnit, PC<FlatFilter> {
    override val productComponents = productOf(this, by)
    override val type get() = by.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is FlatFilter && other.id() == cid
  }

  @Mat
  data class Distinct(@Slot val head: XR.Query, override val loc: Location = Location.Synth): Query, PC<Distinct> {
    override val productComponents = productOf(this, head)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Distinct && other.id() == cid
  }

  @Mat
  data class DistinctOn(@Slot val head: XR.Query, @MSlot val id: XR.Ident, @Slot val by: XR.Expression, override val loc: Location = Location.Synth): Query, PC<DistinctOn> {
    override val productComponents = productOf(this, head, id, by)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is DistinctOn && other.id() == cid
  }

  @Mat
  data class Nested(@Slot val head: XR.Query, override val loc: Location = Location.Synth): XR.Query, PC<Nested> {
    override val productComponents = productOf(this, head)
    override val type get() = head.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Nested && other.id() == cid
  }

  // ************************************************************************************************
  // ****************************************** Infix ********************************************
  // ************************************************************************************************

  // TODO could it also be a XR.Function1/FunctionN?
  @Mat
  data class Infix(@Slot val parts: List<String>, @Slot val params: List<XR>, val pure: Boolean, val transparent: Boolean, override val type: XRType, override val loc: Location = Location.Synth): Query, Expression, PC<Infix> {
    override val productComponents = productOf(this, parts, params)
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Infix && other.id() == cid
  }

  @Mat
  data class Marker(@Slot val name: String, @Slot val expr: XR.Expression?, override val loc: Location = Location.Synth): Query, Expression, PC<Marker> {
    override val productComponents = productOf(this, name, expr)
    override val type get() = XRType.Generic
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Marker && other.id() == cid
  }


  // ************************************************************************************************
  // ****************************************** Function ********************************************
  // ************************************************************************************************

  // Functions are essentially lambdas that are invoked via FunctionApply. Since they can be written into
  // expresison-variables (inside of Encode-Expressions) etc... so they need to be subtypes of XR.Expression.

  @Mat
  data class Function1(@CS val param: XR.Ident, @Slot override val body: XR.Expression, override val loc: Location = Location.Synth): Expression, Labels.Function, PC<Function1> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}

    override val params get() = listOf(param)
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Function1 && other.id() == cid
  }

  @Mat
  data class FunctionN(@CS override val params: List<Ident>, @Slot override val body: XR.Expression, override val loc: Location = Location.Synth): Expression, Labels.Function, PC<FunctionN> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is FunctionN && other.id() == cid
  }

  // ************************************************************************************************
  // ****************************************** Expression ******************************************
  // ************************************************************************************************

  /**
   * Note that the `function` slot can be an expression but in practice its almost always a function
   */
  @Mat
  data class FunctionApply(@Slot val function: Expression, @Slot val args: List<XR.Expression>, override val loc: Location = Location.Synth): Expression, PC<FunctionApply> {
    override val productComponents = productOf(this, function, args)
    override val type get() = function.type
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is FunctionApply && other.id() == cid
  }

  @Mat
  data class BinaryOp(@Slot val a: XR.Expression, @CS val op: BinaryOperator, @Slot val b: XR.Expression, override val loc: Location = Location.Synth) : Expression, PC<BinaryOp> {
    override val productComponents = productOf(this, a, b)
    override val type by lazy {
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is BinaryOp && other.id() == cid
  }

  @Mat
  data class UnaryOp(@CS val op: UnaryOperator, @Slot val expr: XR.Expression, override val loc: Location = Location.Synth) : Expression, PC<UnaryOp> {
    override val productComponents = productOf(this, expr)
    override val type by lazy {
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is UnaryOp && other.id() == cid
  }

  /**
   * It is interesting to note that in Quill an aggregation could be both a Query and what we would define
   * in ExoQuery as an expression. This was based on the idea of monadic aggregation which was based on
   * the API:
   * ```
   * people.groupBy(p -> p.name).map { case (name, aggQuery:Query<Person>) -> aggQuery.map(_.age).max }
   * ```
   * The problem with this kind of API is that while is works well for monadic-collections, it runs counter
   * to the grain of how SQL things the expression/query paradigm. If `aggQuery` is a monadic-datastructure
   * resembling a list, of course it would be operated by a map-function after aggregation. That means
   * that the IR construct regulating it would also be a Query type
   * `Map(people.groupBy(...), a, Aggregation(max, Map(aggQuery, x, x.age)))`
   *
   * However, if the paradigm is more SQL-esque via the use of something like GroupByMap. This would look more like:
   * ```
   * people.groupByMap(p -> p.name)(p -> p.age.max)
   * ```
   * In additiona to being more intutive, it clearly establishes that the `.max` operation is merely an operation
   * on a Int type i.e. `p.age.max`. From a type-laws perspective this makes little sense but remember that
   * our definition of p.age as a Int is merely an emulation. SQL `select x,y,z` clauses are typed as
   * a coproduct of value-types and operation descriptor types such as aggregations, partitions, etc...
   *
   * Therefore, using this paradigm we would like to establish the construct:
   * ```
   * Aggregation(max, p.age)
   * ```
   * Firmly as an expression type.
   *
   * TODO Possibly we don't even need these and can just use GlobalCall instead to do something like GlobalCall("max", p.age)
   */
  @Mat
  data class Aggregation(@CS val op: AggregationOperator, @Slot val expr: XR.Expression, override val loc: Location = Location.Synth): Expression, PC<Aggregation> {
    override val productComponents = productOf(this, expr)
    override val type by lazy {
      when (op) {
        AggregationOperator.`min` -> expr.type // TODO since they could be BooleanValue? When adding String etc... to XRType should probably make all of them like this? Or maybe they shuold be removed due to the reason above.
        AggregationOperator.`max` -> expr.type
        AggregationOperator.`avg` -> XRType.Value
        AggregationOperator.`sum` -> XRType.Value
        AggregationOperator.`size` -> XRType.Value
      }
    }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Aggregation && other.id() == cid
  }

  @Mat
  data class MethodCallName(@Slot val name: XR.FqName, @Slot val originalType: XR.FqName): PC<MethodCallName> {
    override val productComponents = productOf(this, name, originalType)
    companion object {}
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is MethodCallName && other.id() == cid
  }

  @Mat
  data class MethodCall(@Slot val head: XR.Expression, val name: XR.MethodCallName, @Slot val args: List<XR.Expression>, override val type: XRType, override val loc: Location = Location.Synth): Expression, PC<MethodCall> {
    override val productComponents = productOf(this, head, args)
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is MethodCall && other.id() == cid
  }

  @Mat
  data class GlobalCall(val name: XR.FqName, @Slot val args: List<XR.Expression>, override val type: XRType, override val loc: Location = Location.Synth): Expression, PC<GlobalCall> {
    override val productComponents = productOf(this, args)
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is GlobalCall && other.id() == cid
  }


//  /**
//   * The element that unifies query and expression. For example
//   * `people.map(_.age).avg` should be represented as:
//   * `people.map(_.age).map(i -> sum(i)).value whose tree is:
//   * ValueOf(Map(Map(people, x, x.age), i, sum(i)))
//   */
//  data class ValueOf(val head: Query, override val loc: Location = Location.Synth): XR.Expression {
//    override val type: XRType = head.type
//  }


  // **********************************************************************************************
  // ****************************************** Terminal ******************************************
  // **********************************************************************************************

  /**
   * Class representing an identifier that was syntheized at compile-time that will become
   * a identifier at runtime. This is because things like SqlVariable need to be transformed
   * at runtime to have the name that was assigned to them at compile-time.
   */
  @Mat
  data class IdentOrigin(@Slot val runtimeName: BID, val name: String, override val type: XRType, override val loc: Location = Location.Synth, val visibility: Visibility = Visibility.Visible) : XR, Labels.Terminal, PC<IdentOrigin> {

    override val productComponents = productOf(this, runtimeName)
    companion object {}

    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is IdentOrigin && other.id() == cid
  }

  sealed interface Location {
    data class File(val path: String, val row: Int, val col: Int): Location
    object Synth: Location
  }

  // TODO should have some information defining a symbol as "Anonymous" e.g. from the .join clause so use knows that in warnings
  @Mat
  data class Ident(@Slot val name: String, override val type: XRType, override val loc: Location = Location.Synth, val visibility: Visibility = Visibility.Visible) : XR, Labels.Terminal, PC<XR.Ident> {

    override val productComponents = productOf(this, name)
    companion object {
      context(Ident) fun fromThis(name: String) = copy(name = name)
      fun from(ident: Ident): Copy = Copy(ident)
      data class Copy(val host: Ident) {
        operator fun invoke(name: String) = host.copy(name = name)
      }

      val Unused = XR.Ident("unused", XRType.Unknown, XR.Location.Synth)
    }

    override fun toString() = show()
    private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Ident && other.id() == cid
  }

  // ConstType<Const.Boolean>: PC<Const.Boolean>

  sealed class ConstType<T>: PC<ConstType<T>>, XR.Expression {
    abstract val value: T
    override val productComponents by lazy { productOf(this, value) }
    override val type = XRType.Value
    override fun toString() = show()
    companion object {
      data class ConstTypeId<T>(val value: T)
    }
    val cid by lazy { ConstTypeId(value) }
    override fun hashCode() = cid.hashCode()
  }

  sealed interface Const: Expression {
    data class Boolean(override val value: kotlin.Boolean, override val loc: Location = Location.Synth) : ConstType<kotlin.Boolean>(), Const { override fun equals(other: Any?) = other is Const.Boolean && other.value == value }
    data class Char(override val value: kotlin.Char, override val loc: Location = Location.Synth) : ConstType<kotlin.Char>(), Const { override fun equals(other: Any?) = other is Const.Char && other.value == value }
    data class Byte(override val value: kotlin.Int, override val loc: Location = Location.Synth) : ConstType<kotlin.Int>(), Const { override fun equals(other: Any?) = other is Const.Byte && other.value == value }
    data class Short(override val value: kotlin.Short, override val loc: Location = Location.Synth) : ConstType<kotlin.Short>(), Const { override fun equals(other: Any?) = other is Const.Short && other.value == value }
    data class Int(override val value: kotlin.Int, override val loc: Location = Location.Synth) : ConstType<kotlin.Int>(), Const { override fun equals(other: Any?) = other is Const.Int && other.value == value }
    data class Long(override val value: kotlin.Long, override val loc: Location = Location.Synth) : ConstType<kotlin.Long>(), Const { override fun equals(other: Any?) = other is Const.Long && other.value == value }
    data class String(override val value: kotlin.String, override val loc: Location = Location.Synth) : ConstType<kotlin.String>(), Const { override fun equals(other: Any?) = other is Const.String && other.value == value }
    data class Float(override val value: kotlin.Float, override val loc: Location = Location.Synth) : ConstType<kotlin.Float>(), Const { override fun equals(other: Any?) = other is Const.Float && other.value == value }
    data class Double(override val value: kotlin.Double, override val loc: Location = Location.Synth) : ConstType<kotlin.Double>(), Const { override fun equals(other: Any?) = other is Const.Double && other.value == value }
    data class Null(override val loc: Location = Location.Synth): Const {
      override fun toString(): kotlin.String = "Null"
      object Id
      override fun hashCode() = Id.hashCode()
      override fun equals(other: Any?) = other is Null
      override val type = XRType.Value
    }

  }

  @Mat
  data class Product(val name: String, @Slot val fields: List<Pair<String, XR.Expression>>, override val loc: Location = Location.Synth): Expression, PC<Product> {
    override val productComponents = productOf(this, fields)
    override val type by lazy { XRType.Product(name, fields.map { it.first to it.second.type }) }
    companion object {
      operator fun invoke(name: String, loc: Location = Location.Synth, vararg fields: Pair<String, Expression>) = Product(name, fields.toList(), loc)

      fun Tuple(first: XR.Expression, second: XR.Expression, loc: Location = Location.Synth) =
        XR.Product("Tuple", loc, "first" to first, "second" to second)

      fun Triple(first: XR.Expression, second: XR.Expression, third: XR.Expression, loc: Location = Location.Synth) =
        XR.Product("Tuple", loc, "first" to first, "second" to second, "third" to third)

      // WARNING: Use these only when you don't care about the property-values because kotlin doesn't
      // actually have _X tuples.
      fun TupleNumeric(vararg values: XR.Expression, loc: Location = Location.Synth) =
        TupleNumeric(values.toList(), loc)
      fun TupleNumeric(values: List<XR.Expression>, loc: Location = Location.Synth) =
        Product("Tuple${values.size}", values.withIndex().map { (idx, v) -> "_${idx+1}" to v }, loc)

      // Used by the SqlQuery clauses to wrap identifiers into a row-class
      fun fromProductIdent(id: XR.Ident): XR.Product {
        val identName = id.name
        val type = id.type
        return when (type) {
          // tpe: XRType.Prod(a->V,b->V), id: Id("foo",tpe) ->
          //   XR.Prod(id, a->Prop(id,"a"), b->Prop(id,"b"))
          is XRType.Product ->
            Product(identName, type.fields.map { (fieldName, _) -> fieldName to XR.Property(id, fieldName, Visibility.Visible, Location.Synth) }, Location.Synth)
          else ->
            // Not sure if this case is possible
            Product("<Generated>", listOf(identName to id), Location.Synth)
        }
      }
    }

    override fun toString() = show()
    private val cid = id()
    override fun hashCode() = cid.hashCode()
    override fun equals(other: Any?) = other is Product && other.id() == cid
  }

  sealed interface Visibility {
    object Hidden: Visibility { override fun toString() = "Hidden" }
    object Visible: Visibility { override fun toString() = "Visible" }
  }

  // NOTE: No renameable because in ExoQuery properties will be renamed when created on the parent object i.e.
  // (before any phases happen) from the field annotation on the entity object.
  @Mat
  data class Property(@Slot val of: XR.Expression, @Slot val name: String, val visibility: Visibility = Visibility.Visible, override val loc: Location = Location.Synth) : XR.Expression, PC<Property> {
    override val productComponents = productOf(this, of, name)
    override val type: XRType by lazy {
      when (val tpe = of.type) {
        is XRType.Product -> tpe.getField(name) ?: XRType.Unknown
        else -> XRType.Unknown
      }
    }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode() = cid.hashCode()
    override fun equals(other: Any?) = other is Property && other.id() == cid
  }

  @Mat
  data class Block(@Slot val stmts: List<Variable>, @Slot val output: XR.Expression, override val loc: Location = Location.Synth) : XR.Expression, PC<Block> {
    override val productComponents = productOf(this, stmts, output)
    override val type: XRType by lazy { output.type }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode() = cid.hashCode()
    override fun equals(other: Any?) = other is Block && other.id() == cid
  }

  @Mat
  data class When(@Slot val branches: List<Branch>, @Slot val orElse: XR.Expression, override val loc: Location = Location.Synth) : Expression, PC<When> {
    override val productComponents = productOf(this, branches, orElse)
    override val type: XRType by lazy { branches.lastOrNull()?.type ?: XRType.Unknown }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode() = cid.hashCode()
    override fun equals(other: Any?) = other is When && other.id() == cid
  }

  @Mat
  data class Branch(@Slot val cond: XR.Expression, @Slot val then: XR.Expression, override val loc: Location = Location.Synth) : XR, PC<Branch> {
    override val productComponents = productOf(this, cond, then)
    override val type: XRType by lazy { then.type }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode() = cid.hashCode()
    override fun equals(other: Any?) = other is Branch && other.id() == cid
  }

  @Mat
  data class Variable(@Slot val name: XR.Ident, @Slot val rhs: XR.Expression, override val loc: Location = Location.Synth): XR, PC<Variable> {
    override val productComponents = productOf(this, name, rhs)
    override val type: XRType by lazy { rhs.type }
    companion object {}
    override fun toString() = show()
    private val cid = id()
    override fun hashCode() = cid.hashCode()
    override fun equals(other: Any?) = other is Variable && other.id() == cid
  }
}

fun XR.isBottomTypedTerminal() =
  this is XR.Labels.Terminal && (this.type is XRType.Null || this.type is XRType.Generic || this.type is XRType.Unknown)

fun XR.isTerminal() =
  this is XR.Labels.Terminal

fun XR.Labels.Terminal.withType(type: XRType): XR.Expression =
  when (this) {
    is XR.Ident -> XR.Ident(name, type, loc)
    is XR.IdentOrigin -> XR.IdentOrigin(runtimeName, name, type, loc)
  }