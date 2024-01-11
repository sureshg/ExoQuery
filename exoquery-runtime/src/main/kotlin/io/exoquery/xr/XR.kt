package io.exoquery.xr

import io.decomat.ProductClass
import io.decomat.Matchable as Mat
import io.decomat.Component as Slot
import io.decomat.productComponentsOf as productOf
import io.decomat.HasProductClass as PC

sealed class XRType {
  data class Product(val name: String, val fields: List<Pair<String, XRType>>): XRType() {
    private val fieldsHash by lazy { fields.toMap() }
    fun getField(name: String) =
      if (fields.size < 5)
        fields.find { it.first == name }?.second
      else
        fieldsHash.get(name)
  }

  sealed class Boolean: XRType()
  object BooleanValue: Boolean()
  object BooleanExpression: Boolean()

  object Unknown: XRType()
  object Generic: XRType()

  object Value: XRType() {
    override fun toString(): String {
      return "Value"
    }
  }
}


// TODO every XR needs an XR type
sealed class XR {
  abstract val type: XRType

  sealed class JoinType {
    object Inner: JoinType()
    object Left: JoinType()
  }

  sealed class Query(): XR()

  @Mat
  data class Entity(@Slot val name: String, override val type: XRType): Query(), PC<Entity> {
    override val productComponents = productOf(this, name)
    companion object {}
  }

  @Mat
  data class Map(@Slot val a: XR, val ident: Ident, @Slot val b: XR): Query(), PC<Map> {
    override val productComponents = productOf(this, a, b)
    override val type get() = b.type
    companion object {}
  }
  //data class FlatMap(val a: XR, val ident: Ident, val b: XR): Query() {
  //  override val type get() = b.type
  //}

  @Mat
  data class FlatMap(@Slot val a: XR, val ident: Ident, @Slot val b: XR): Query(), PC<FlatMap> {
    override val productComponents = productOf(this, a, b)
    override val type get() = b.type
    companion object {}
  }

  @Mat
  data class Filter(@Slot val a: XR, val ident: Ident, @Slot val b: XR): Query(), PC<Filter> {
    override val productComponents = productOf(this, a, b)
    override val type get() = a.type
    companion object {}
  }

  @Mat
  data class FlatJoin(val joinType: JoinType, @Slot val a: XR, val aliasA: Ident, @Slot val on: XR): Query(), PC<FlatJoin> {
    override val productComponents = productOf(this, a, on)
    override val type get() = a.type
    companion object {}
  }

  sealed class Function: XR()

  @Mat
  data class Function1(val param: Ident, @Slot val body: XR): Function(), PC<Function1> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}
  }

  @Mat
  data class FunctionN(val params: List<Ident>, @Slot val body: XR): Function(), PC<FunctionN> {
    override val productComponents = productOf(this, body)
    override val type get() = body.type
    companion object {}
  }

  @Mat
  data class BinaryOp(@Slot val a: XR, val op: BinaryOperator, @Slot val b: XR) : XR(), PC<BinaryOp> {
    override val productComponents = productOf(this, a, b)
    override val type get() =
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    companion object {}
  }

  @Mat
  data class UnaryOp(val op: UnaryOperator, @Slot val expr: XR) : XR(), PC<UnaryOp> {
    override val productComponents = productOf(this, expr)
    override val type get() =
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
    companion object {}
  }

  @Mat
  data class Ident(@Slot val name: String, override val type: XRType) : XR(), PC<Ident> {
    override val productComponents = productOf(this, name)
    companion object {}
  }

  @Mat
  data class Nested(@Slot val query: XR): XR(), PC<Nested> {
    override val productComponents = productOf(this, query)
    override val type get() = query.type
    companion object {}
  }

  @Mat
  data class Marker(@Slot val query: XR): XR(), PC<Marker> {
    override val productComponents = productOf(this, query)
    override val type get() = query.type
    companion object {}
  }

  sealed class Const: XR() {
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
  data class Property(@Slot val of: XR, @Slot val name: String) : XR(), PC<Property> {
    override val productComponents = productOf(this, of, name)
    override val type: XRType
      get() =
      when (val tpe = of.type) {
        is XRType.Product -> tpe.getField(name) ?: XRType.Unknown
        else -> XRType.Unknown
      }
    companion object {}
  }

  @Mat
  data class Block(@Slot val stmts: List<Variable>, @Slot val output: XR) : XR(), PC<Block> {
    override val productComponents = productOf(this, stmts, output)
    override val type: XRType get() = output.type
    companion object {}
  }

  @Mat
  data class When(@Slot val branches: List<Branch>, @Slot val orElse: Branch) : XR(), PC<When> {
    override val productComponents = productOf(this, branches, orElse)
    override val type: XRType get() = branches.lastOrNull()?.type ?: XRType.Unknown
    companion object {}
  }
  @Mat
  data class Branch(@Slot val cond: XR, @Slot val then: XR) : XR(), PC<Branch> {
    override val productComponents = productOf(this, cond, then)
    override val type: XRType get() = then.type
    companion object {}
  }

  @Mat
  data class Variable(@Slot val name: String, @Slot val rhs: XR): XR(), PC<Variable> {
    override val productComponents = productOf(this, name, rhs)
    override val type: XRType get() = rhs.type
    companion object {}
  }
}
