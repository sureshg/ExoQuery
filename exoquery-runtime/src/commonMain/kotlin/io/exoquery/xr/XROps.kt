package io.exoquery.xr

import io.exoquery.BID

// Can't use || or && chars because they don't work with linuxX64
infix fun XR.Expression.`+or+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, BooleanOperator.or, other, this.loc)
infix fun XR.Expression.`+and+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, BooleanOperator.and, other, this.loc)
infix fun XR.Expression.`+==+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, EqualityOperator.`==`, other, this.loc)
infix fun XR.Expression.`+!=+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, EqualityOperator.`!=`, other, this.loc)
infix fun XR.Expression.`+'+'+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, StringOperator.`+`, other, this.loc)
infix fun XR.Expression.`+++`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, NumericOperator.plus, other, this.loc)

fun XR.swapTags(tagMap: Map<BID, BID>): XR =
  when (this) {
    is XR.Expression -> swapTags(tagMap)
    // TODO introduce other clauses when we have them
  }

//fun XR.Expression.emptyFiletags(): XR.Expression =
//  TransformXR.Expression(this) {
//    with(it) {
//      when (this) {
//        is XR.Location.File -> XR.Location.File("", 0, 0)
//        else -> null
//      }
//    }
//  }

fun XR.Expression.swapTags(tagMap: Map<BID, BID>): XR.Expression =
  TransformXR.Expression(this) {
    with(it) {
      when (this) {
        is XR.TagForParam ->
          tagMap.get(id)?.let { XR.TagForParam.csf(it)(this) }
        is XR.TagForSqlExpression -> tagMap.get(id)?.let { XR.TagForSqlExpression.csf(it)(this) }
        // Transform XR expects a "partial function" i.e. for the user to return 'null'
        // when there is no transform for the given tree. Then it knows to recurse further in.
        else -> null
      }
    }
  }
