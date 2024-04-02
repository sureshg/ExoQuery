package io.exoquery

import io.exoquery.xr.XR
import io.exoquery.xr.XRType

// This is an SqlExpression that can be spliced using the .invoke() (or .asValue) operators
// it can be converted into a value by using the asQuery
data class SqlInfixValue<T>(override val xr: XR.Infix, override val binds: DynamicBinds): SqlExpression<T> {
  fun asQuery(): Query<T> =
    QueryContainer<T>(xr, binds).withReifiedRuntimes()
}

/** Makes the XRType a boolean condition which means it can be used in WHERE/IF clauses with additional wrapping */
fun SqlInfixValue<Boolean>.asCond() =
  this.copy(xr = this.xr.copy(type = XRType.BooleanExpression))


fun interpolatorBody(): Nothing = throw IllegalStateException("interpolator could not be invoked")

object SQL {
  operator fun <T> invoke(expr: context(EnclosedExpression) () -> String): SqlInfixValue<T> = interpolatorBody()
  operator fun <T> invoke(expr: String): SqlInfixValue<T> = interpolatorBody()
  fun <T> interpolate(parts: List<String>, params: List<XR>, type: XRType, binds: DynamicBinds, loc: XR.Location): SqlInfixValue<T> =
    SqlInfixValue<T>(XR.Infix(parts, params, false, false, type, loc), binds)
}

