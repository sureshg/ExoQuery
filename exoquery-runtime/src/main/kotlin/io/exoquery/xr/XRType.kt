package io.exoquery.xr

import kotlinx.serialization.Serializable

// Formerly Quat
@Serializable
sealed class XRType {
  fun isAbstract() =
    when (this) {
      is Generic -> true
      is Unknown -> true
      else -> false
    }

  fun nonAbstract() = !isAbstract()

  @Serializable
  data class Product(val name: String, val fields: List<Pair<String, XRType>>): XRType() {
    private val fieldsHash by lazy { fields.toMap() }
    /**
     * The underlying impelmentation in kotlin o List<Pair<String, XRType>>.toMap() is a LinkedHashMap
     * if that changes in the future than we will need to change the result of this to explicitly be a LinkedHashMap.
     */
    fun fieldsHash() = fieldsHash
    fun getField(name: String) =
      if (fields.size < 5)
        fields.find { it.first == name }?.second
      else
        fieldsHash.get(name)
  }

  @Serializable
  sealed class Boolean: XRType()
  @Serializable object BooleanValue: Boolean()
  @Serializable object BooleanExpression: Boolean()

  object Unknown: XRType()
  object Null: XRType()
  object Generic: XRType()

  object Value: XRType() {
    override fun toString(): String {
      return "Value"
    }
  }

  fun shortString(): String = when(this) {
    is XRType.Product -> "${name}(${fields.map { (k, v) -> "${k}:${v.shortString()}" }.joinToString(",")})"
    is XRType.Generic           -> "<G>"
    is XRType.Unknown           -> "<U>"
    is XRType.Value             -> "V"
    is XRType.Null              -> "N"
    is XRType.BooleanValue      -> "BV"
    is XRType.BooleanExpression -> "BE"
  }

  companion object {}
}

fun XRType.leastUpperType(other: XRType): XRType? =
  when {
    this is XRType.Generic                            -> other
    this is XRType.Unknown                            -> other
    this is XRType.Null                               -> other
    other is XRType.Generic                            -> other
    other is XRType.Unknown                            -> other
    other is XRType.Null                               -> other
    this is XRType.Value && other is XRType.Value                         -> XRType.Value
    this is XRType.BooleanExpression && other is XRType.BooleanExpression -> XRType.BooleanExpression
    this is XRType.BooleanValue && other is XRType.BooleanValue           -> XRType.BooleanValue
    this is XRType.BooleanValue && other is XRType.BooleanExpression      -> XRType.BooleanValue
    this is XRType.BooleanExpression && other is XRType.BooleanValue      -> XRType.BooleanValue
    this is XRType.Value && other is XRType.BooleanValue                  -> XRType.Value
    this is XRType.Value && other is XRType.BooleanExpression             -> XRType.Value
    this is XRType.BooleanValue && other is XRType.Value                  -> XRType.Value
    this is XRType.BooleanExpression && other is XRType.Value             -> XRType.Value
    this is XRType.Product && other is XRType.Product                     -> this.leastUpperTypeProduct(other)
    else -> null
  }

fun XRType.Product.leastUpperTypeProduct(other: XRType.Product): XRType.Product {
  val fields = this.fields.zipNotNullWith(other.fieldsHash())
  val newFields = fields.map { (name, type, otherType) ->
    val newType = type.leastUpperType(otherType) ?: run {
      // TODO Log warning about the invalid reduction
      XRType.Unknown
    }
    name to newType
  }
  return XRType.Product(name, newFields)
}

fun <T> List<Pair<String, T>>.zipNotNullWith(other:Map<String, T>) =
  this.mapNotNull { (name, type) ->
    other[name]?.let { otherName ->
      Triple<String, T, T>(name, type, otherName)
    }
  }
