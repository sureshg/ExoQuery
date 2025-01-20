package io.exoquery.norm

import io.exoquery.xr.StatelessTransformer
import io.exoquery.xr.XR

// Convert all instances of CustomQuery (that are convertable to regular XR).
class NormalizeCustomQueries: StatelessTransformer {
  override fun invoke(xr: XR.Query): XR.Query =
    when {
      xr is XR.CustomQueryRef && xr.customQuery is XR.CustomQuery.Convertable -> xr.customQuery.toQueryXR()
      else -> super.invoke(xr)
    }
}
