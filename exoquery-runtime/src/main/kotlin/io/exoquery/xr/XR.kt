package io.exoquery.xr

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
  data class Entity(val name: String, override val type: XRType): Query()
  data class Map(val a: XR, val ident: Ident, val b: XR): Query() {
    override val type get() = b.type
  }
  data class FlatMap(val a: XR, val ident: Ident, val b: XR): Query() {
    override val type get() = b.type
  }
  data class FlatJoin(val joinType: JoinType, val a: XR, val aliasA: Ident, val on: XR): Query() {
    override val type get() = a.type
  }

  sealed class Function: XR()
  data class Function1(val param: Ident, val body: XR): Function() {
    override val type get() = body.type
  }


  data class BinaryOp(val a: XR, val op: BinaryOperator, val b: XR) : XR() {
    override val type get() =
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
  }

  data class UnaryOp(val op: UnaryOperator, val expr: XR) : XR() {
    override val type get() =
      when (op) {
        is YieldsBool -> XRType.BooleanExpression
        else -> XRType.Value
      }
  }

  data class Ident(val name: String, override val type: XRType) : XR()

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

  data class Property(val of: XR, val name: String) : XR() {
    override val type: XRType
      get() =
      when (val tpe = of.type) {
        is XRType.Product -> tpe.getField(name) ?: XRType.Unknown
        else -> XRType.Unknown
      }
  }

  data class Block(val stmts: List<Variable>, val output: XR) : XR() {
    override val type: XRType get() = output.type
  }
  data class When(val branches: List<Branch>) : XR() {
    // TODO When should probably have an else-branch
    override val type: XRType get() = branches.lastOrNull()?.type ?: XRType.Unknown
  }

  data class Branch(val cond: XR, val then: XR): XR() {
    override val type: XRType get() = then.type
  }
  data class Variable(val name: String, val rhs: XR): XR() {
    override val type: XRType get() = rhs.type
  }

}
