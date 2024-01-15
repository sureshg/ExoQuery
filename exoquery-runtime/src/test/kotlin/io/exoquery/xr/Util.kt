@file:Suppress("DANGEROUS_CHARACTERS")

package io.exoquery.xr

operator fun XR.Ident.Companion.invoke(id: String) = XR.Ident(id, XRType.Generic)
operator fun XR.Entity.Companion.invoke(id: String) = XR.Entity(id, XRType.Generic)

infix fun XR.Expression.`+||+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, BooleanOperator.or, other)
infix fun XR.Expression.`+&&+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, BooleanOperator.and, other)
infix fun XR.Expression.`+==+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, EqualityOperator.`==`, other)
infix fun XR.Expression.`+!=+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, EqualityOperator.`!=`, other)