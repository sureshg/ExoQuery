package io.exoquery

import io.exoquery.xr.XR

// The contents of this constructed directly as Kotlin IR nodes with the expressions dynamically inside e.g.
// IrCall(IrConstructor(Sym("RuntimeBindValue.String"), listOf(IrString("Joe")))
sealed interface RuntimeBindValue {
  data class RuntimeQuery(val value: Query<*>): RuntimeBindValue
  data class RuntimeExpression(val value: SqlExpression<*>): RuntimeBindValue
}