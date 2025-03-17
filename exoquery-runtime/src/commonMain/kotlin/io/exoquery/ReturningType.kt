package io.exoquery

import io.exoquery.xr.XR

sealed interface ReturningType {
  data object None : ReturningType
  data class Explicit(val columns: List<String>) : ReturningType
  data object ClauseInQuery : ReturningType

  companion object {
    fun fromActionXR(action: XR.Action): ReturningType = when {
      action is XR.Returning && action.output is XR.Returning.Kind.Expression ->
        ClauseInQuery
      action is XR.Returning && action.output is XR.Returning.Kind.Keys ->
        // for properties e.g. name.first etc... we only care about the outermost property name because nested objects are ignored in the functioning of the actual SQL
        Explicit(action.output.keys.map { it.name })
      else ->
        None
    }
  }
}
