package io.exoquery.norm

import io.decomat.*
import io.exoquery.sql.ProtractQuat
import io.exoquery.xr.`+!=+`
import io.exoquery.xr.`+and+`
import io.exoquery.xr.EqEq
import io.exoquery.xr.IsTypeProduct
import io.exoquery.xr.NullIfNullOrX
import io.exoquery.xr.NullXR

import io.exoquery.xr.StatelessTransformer
import io.exoquery.xr.XR
import io.exoquery.xr.XRType

// Convert all instances of CustomQuery (that are convertable to regular XR).
object ExpandProductNullChecks : StatelessTransformer {
  override fun invoke(xr: XR.Expression): XR.Expression =
    on(xr).match(
      case(XR.BinaryOp.EqEq[IsTypeProduct(), NullXR()]).thenThis { core, nullCheck ->
        val newCore = invoke(core)
        val props =
          ProtractQuat(false)(core.type as XRType.Product, newCore).map { (prop, _) ->
            prop `+!=+` XR.Const.Null(nullCheck.loc)
          }
        props.reduce { a: XR.Expression, b: XR.Expression -> a `+and+` b }
      }
    ) ?: super.invoke(xr)
}
