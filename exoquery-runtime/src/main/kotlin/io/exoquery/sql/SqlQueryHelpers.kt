package io.exoquery.sql

import io.exoquery.xr.`+&&+`
import io.exoquery.xr.XR
import io.exoquery.xrError

data class LayerComponents(val grouping: SqlQueryApply.Layer.Grouping?, val sorting: SqlQueryApply.Layer.Sorting?, val filtering: SqlQueryApply.Layer.Filtering?)

fun List<SqlQueryApply.Layer>.findComponentsOrNull(): LayerComponents {
  val groupings = this.mapNotNull { if (it is SqlQueryApply.Layer.Grouping) it else null }
  if (groupings.size > 1) xrError("Multiple groupings detected, this is illegal:\n" + groupings.map { it.groupBy }.joinToString("\n"))

  val sortings = this.mapNotNull { if (it is SqlQueryApply.Layer.Sorting) it else null }
  if (sortings.size > 1) xrError("Multiple sortings detected, this is illegal:\n" + sortings.map { it.sortedBy }.joinToString("\n"))

  val filterings = this.mapNotNull { if (it is SqlQueryApply.Layer.Filtering) it else null }
  if (filterings.size > 1) xrError("Multiple sortings detected, this is illegal:\n" + filterings.map { it.where }.joinToString("\n"))

  return LayerComponents(groupings.firstOrNull(), sortings.firstOrNull(), filterings.firstOrNull())
}


fun combineWhereClauses(expr: XR.Expression?, combineWith: XR.Expression) =
  if (expr != null) expr `+&&+` combineWith
  else combineWith