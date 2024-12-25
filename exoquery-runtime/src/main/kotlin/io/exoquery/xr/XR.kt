package io.exoquery.xr

import io.exoquery.xr.id
import io.exoquery.BID
import io.exoquery.printing.PrintXR
import io.exoquery.printing.format
import io.exoquery.util.dropLastSegment
import io.exoquery.util.takeLastSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
@Serializable
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
  abstract val loc: XR.Location

  fun show(pretty: Boolean = false): String {
    return PrintXR.BlackWhite.invoke(this).plainText
  }

  sealed interface Location {
    data class File(val path: String, val row: Int, val col: Int): Location
    object Synth: Location
  }


  fun showRaw(color: Boolean = true): String {
    val str = PrintXR()(this)
    return if (color) str.toString() else str.plainText
  }

  @Serializable
  sealed interface Expression: XR



  @Serializable
  @Mat
  data class BinaryOp(@Slot val a: XR.Expression, @CS val op: BinaryOperator, @Slot val b: XR.Expression, override val loc: Location = Location.Synth) : Expression, PC<BinaryOp> {
    // TODO mark this @Transient in the PC class?
    @Transient override val productComponents = productOf(this, a, b)
    override val type by lazy {
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    }
    companion object {}
    override fun toString() = show()
    @Transient private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is BinaryOp && other.id() == cid
  }

  @Serializable
  @Mat
  data class UnaryOp(@CS val op: UnaryOperator, @Slot val expr: XR.Expression, override val loc: Location = Location.Synth) : Expression, PC<UnaryOp> {
    @Transient override val productComponents = productOf(this, expr)
    override val type by lazy {
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    }
    companion object {}
    override fun toString() = show()
    @Transient private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is UnaryOp && other.id() == cid
  }

  @Serializable
  @Mat
  data class Ident(@Slot val name: String, override val type: XRType, override val loc: Location = Location.Synth, val visibility: Visibility = Visibility.Visible) : XR, Labels.Terminal, PC<XR.Ident> {

    @Transient override val productComponents = productOf(this, name)
    companion object {
      context(Ident) fun fromThis(name: String) = copy(name = name)
      fun from(ident: Ident): Copy = Copy(ident)
      data class Copy(val host: Ident) {
        operator fun invoke(name: String) = host.copy(name = name)
      }

      val Unused = XR.Ident("unused", XRType.Unknown, XR.Location.Synth)
    }

    override fun toString() = show()
    @Transient private val cid = id()
    override fun hashCode(): Int = cid.hashCode()
    override fun equals(other: Any?): Boolean = other is Ident && other.id() == cid
  }

  // ConstType<Const.Boolean>: PC<Const.Boolean>

  @Serializable
  sealed class ConstType<T>: PC<ConstType<T>>, XR.Expression {
    abstract val value: T
    override val productComponents by lazy { productOf(this, value) }
    @Transient override val type = XRType.Value
    override fun toString() = show()
    companion object {
      data class ConstTypeId<T>(val value: T)
    }
    val cid by lazy { ConstTypeId(value) }
    override fun hashCode() = cid.hashCode()
  }

  @Serializable
  sealed interface Const: Expression {
    @Serializable data class Boolean(override val value: kotlin.Boolean, override val loc: Location = Location.Synth) : ConstType<kotlin.Boolean>(), Const { override fun equals(other: Any?) = other is Const.Boolean && other.value == value }
    @Serializable data class Char(override val value: kotlin.Char, override val loc: Location = Location.Synth) : ConstType<kotlin.Char>(), Const { override fun equals(other: Any?) = other is Const.Char && other.value == value }
    @Serializable data class Byte(override val value: kotlin.Int, override val loc: Location = Location.Synth) : ConstType<kotlin.Int>(), Const { override fun equals(other: Any?) = other is Const.Byte && other.value == value }
    @Serializable data class Short(override val value: kotlin.Short, override val loc: Location = Location.Synth) : ConstType<kotlin.Short>(), Const { override fun equals(other: Any?) = other is Const.Short && other.value == value }
    @Serializable data class Int(override val value: kotlin.Int, override val loc: Location = Location.Synth) : ConstType<kotlin.Int>(), Const { override fun equals(other: Any?) = other is Const.Int && other.value == value }
    @Serializable data class Long(override val value: kotlin.Long, override val loc: Location = Location.Synth) : ConstType<kotlin.Long>(), Const { override fun equals(other: Any?) = other is Const.Long && other.value == value }
    @Serializable data class String(override val value: kotlin.String, override val loc: Location = Location.Synth) : ConstType<kotlin.String>(), Const { override fun equals(other: Any?) = other is Const.String && other.value == value }
    @Serializable data class Float(override val value: kotlin.Float, override val loc: Location = Location.Synth) : ConstType<kotlin.Float>(), Const { override fun equals(other: Any?) = other is Const.Float && other.value == value }
    @Serializable data class Double(override val value: kotlin.Double, override val loc: Location = Location.Synth) : ConstType<kotlin.Double>(), Const { override fun equals(other: Any?) = other is Const.Double && other.value == value }
    @Serializable data class Null(override val loc: Location = Location.Synth): Const {
      override fun toString(): kotlin.String = "Null"
      object Id
      override fun hashCode() = Id.hashCode()
      override fun equals(other: Any?) = other is Null
      @Transient override val type = XRType.Value
    }
  }

  @Serializable
  sealed interface Visibility {
    @Serializable object Hidden: Visibility { override fun toString() = "Hidden" }
    @Serializable object Visible: Visibility { override fun toString() = "Visible" }
  }
}


fun XR.isBottomTypedTerminal() =
  this is XR.Labels.Terminal && (this.type is XRType.Null || this.type is XRType.Generic || this.type is XRType.Unknown)

fun XR.isTerminal() =
  this is XR.Labels.Terminal

fun XR.Labels.Terminal.withType(type: XRType): XR.Expression =
  when (this) {
    is XR.Ident -> XR.Ident(name, type, loc)
  }