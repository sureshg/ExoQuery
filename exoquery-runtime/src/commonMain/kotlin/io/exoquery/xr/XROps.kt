package io.exoquery.xr

import io.decomat.Components2M
import io.decomat.Pattern
import io.decomat.customPattern2M
import io.exoquery.BID

// Can't use || or && chars because they don't work with linuxX64
infix fun XR.Expression.`+or+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.or, other, this.loc)
infix fun XR.Expression.`+and+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.and, other, this.loc)
infix fun XR.Expression.`+==+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.`==`, other, this.loc)
infix fun XR.Expression.`+!=+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.`!=`, other, this.loc)
infix fun XR.Expression.`+'+'+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.strPlus, other, this.loc)
infix fun XR.Expression.`+++`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.plus, other, this.loc)

fun XR.Query.swapTags(tagMap: Map<BID, BID>): XR.Query =
  SwapTagsTransformer(tagMap).invoke(this)

fun XR.Expression.swapTags(tagMap: Map<BID, BID>): XR.Expression =
  SwapTagsTransformer(tagMap).invoke(this)

internal class SwapTagsTransformer(tagMap: Map<BID, BID>): TransformXR(
  transformExpression = {
    with (it) {
      when (this) {
        is XR.TagForParam ->
          tagMap.get(id)?.let { XR.TagForParam.csf(it)(this) }
        is XR.TagForSqlExpression -> tagMap.get(id)?.let { XR.TagForSqlExpression.csf(it)(this) }
        // Transform XR expects a "partial function" i.e. for the user to return 'null'
        // when there is no transform for the given tree. Then it knows to recurse further in.
        else -> null
      }
    }
  },
  transformQuery = {
    with(it) {
      when (this) {
        is XR.TagForSqlQuery -> tagMap.get(id)?.let { XR.TagForSqlQuery.csf(it)(this) }
        else -> null
      }
    }
  }
)

fun XR.Map.containsImpurities(): Boolean =
  CollectXR(this) {
    with(it) {
      when {
        this is XR.GlobalCall && !this.isPure() -> this
        this is XR.MethodCall && !this.isPure() -> this
        this is XR.Free && !this.pure -> this
        else -> null
      }
    }
  }.isNotEmpty()


/*
def unapply(ast: Ast) =
CollectAst(ast) {
  case agg: Aggregation          => agg
    case inf: Free if (!inf.pure) => inf
}.nonEmpty
 */

val XR.Map.Companion.DistinctHead get() = DistinctHeadMap()
class DistinctHeadMap() {
  operator fun <AP: Pattern<Q>, Q: XR.Query, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
    customPattern2M("XR.Map.DistinctHead", x, y) { it: XR.Map ->
      with(it) {
        when {
          this.head is XR.Distinct -> Components2M(this.head.head, this.id, this.body)
          else -> null
        }
      }
    }
}


val XR.SortBy.Companion.DistinctHead get() = DistinctHeadMatchSortBy()
class DistinctHeadMatchSortBy() {
  operator fun <AP: Pattern<Q>, Q: XR.Query, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
    customPattern2M("XR.SortBy.DistinctHead", x, y) { it: XR.SortBy ->
      with(it) {
        when {
          this.head is XR.Distinct -> Components2M(this.head.head, this.id, this.criteria)
          else -> null
        }
      }
    }
}


object CID {
  val kotlin_String = XR.ClassId("kotlin.String")
  val kotlin_Int = XR.ClassId("kotlin.Int")
  val kotlin_Long = XR.ClassId("kotlin.Long")
  val kotlin_Short = XR.ClassId("kotlin.Short")
  val kotlin_Double = XR.ClassId("kotlin.Double")
  val kotlin_Float = XR.ClassId("kotlin.Float")
  val kotlin_Boolean = XR.ClassId("kotlin.Boolean")
}

fun XR.ClassId.isWholeNumber(): Boolean =
  when(this) {
    CID.kotlin_Int, CID.kotlin_Long, CID.kotlin_Short -> true
    else -> false
  }

fun XR.ClassId.isFloatingPoint(): Boolean =
  when(this) {
    CID.kotlin_Double, CID.kotlin_Float -> true
    else -> false
  }

fun XR.ClassId.isNumeric(): Boolean =
  when(this) {
    CID.kotlin_Int, CID.kotlin_Long, CID.kotlin_Short, CID.kotlin_Double, CID.kotlin_Float -> true
    else -> false
  }

fun String.isConverterFunction(): Boolean =
  when(this) {
    "toLong", "toInt", "toShort", "toDouble", "toFloat", "toBoolean" -> true
    else -> false
  }
