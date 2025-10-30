package io.exoquery.printing

import io.exoquery.lang.SqlQueryModel
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.XR

interface HasPhasePrinting {
  abstract val traceConf: TraceConfig
  abstract val traceType: TraceType
  abstract val trace: Tracer

  fun title(label: String): String =
    "=".repeat(10) + " $label " + "=".repeat(10)


//  def title[T](label: String, traceType: TraceType = TraceType.Standard): T => T =
//    trace[T](("=".repeat(10)) + s" $label " + ("=".repeat(10)), 0, traceType)

  fun demarcate(heading: String, q: XR.Query) {
    trace.print(title("$heading"))
    trace.interpolate({ listOf("", "") }, { listOf(q) }).andLog()
  }

  fun demarcate(heading: String, q: SqlQueryModel) {
    // Make sure to compute q.showRaw() outside of the trace parameter. Otherwise in some situations fansi blow up. Not sure exactly why.
    val titleString = title("$heading")
    trace("${titleString}").andLog()
    val queryString = q.showRaw()
    trace("${queryString}").andLog()
    //trace.interpolate({ listOf("", "") }, { listOf(q) }).andLog()
  }


//  private def demarcate(heading: String) =
//    ((ast: Query) => title(s"(Normalize) $heading", TraceType.Normalizations)(ast))
}
