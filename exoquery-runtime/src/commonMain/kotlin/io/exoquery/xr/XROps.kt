package io.exoquery.xr

import io.exoquery.BID

// Can't use || or && chars because they don't work with linuxX64
infix fun XR.Expression.`+or+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, BooleanOperator.or, other, this.loc)
infix fun XR.Expression.`+and+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, BooleanOperator.and, other, this.loc)
infix fun XR.Expression.`+==+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, EqualityOperator.`==`, other, this.loc)
infix fun XR.Expression.`+!=+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, EqualityOperator.`!=`, other, this.loc)
infix fun XR.Expression.`+'+'+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, StringOperator.`+`, other, this.loc)
infix fun XR.Expression.`+++`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, NumericOperator.plus, other, this.loc)

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
