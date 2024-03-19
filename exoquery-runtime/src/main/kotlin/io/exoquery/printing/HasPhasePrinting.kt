package io.exoquery.printing

import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.XR

interface HasPhasePrinting {
  abstract val traceConf: TraceConfig
  abstract val traceType: TraceType
  abstract val tracer: Tracer

  fun title(label: String): String =
    "=".repeat(10) + " $label " + "=".repeat(10)


//  def title[T](label: String, traceType: TraceType = TraceType.Standard): T => T =
//    trace[T](("=".repeat(10)) + s" $label " + ("=".repeat(10)), 0, traceType)

  fun demarcate(heading: String, q: XR.Query) =
    if (traceConf.enabledTraces.contains(traceType)) {
      println(
        tracer.interpolate({listOf(title("$heading"), "")}, {listOf(q)})
      )
    } else {
      Unit
    }


//  private def demarcate(heading: String) =
//    ((ast: Query) => title(s"(Normalize) $heading", TraceType.Normalizations)(ast))
}