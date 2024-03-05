package io.exoquery

import io.exoquery.xr.XR

// The contents of this constructed directly as Kotlin IR nodes with the expressions dynamically inside e.g.
// IrCall(IrConstructor(Sym("RuntimeBindValue.String"), listOf(IrString("Joe")))
sealed interface RuntimeBindValue {
  data class SqlVariableIdent(val value: kotlin.String): RuntimeBindValue
  data class RuntimeQuery(val value: XR.Query): RuntimeBindValue
}