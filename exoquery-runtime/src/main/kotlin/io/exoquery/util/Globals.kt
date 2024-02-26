package io.exoquery.util

object Globals {
  // TODO for KMP use kotlinx.cinterop.* from kotlin-stdlib-common
  //      see https://stackoverflow.com/a/55002326
  private fun variable(propName: String, envName: String, default: String) =
    System.getProperty(propName) ?: System.getenv(envName) ?: default

  val querySubexpand: Boolean = cache("exo.trace.color", variable("exo.trace.color", "exo_trace_color,", "true").toBoolean())
  val traceColors get() = cache("exo.trace.color", variable("exo.trace.color", "exo_trace_color,", "false").toBoolean())
  val traceEnabled get() = cache("exo.trace.enabled", variable("exo.trace.enabled", "exo_trace_enabled", "false").toBoolean())

  fun resetCache(): Unit                        = cacheMap.clear()
  private val cacheMap: MutableMap<String, Any> = mutableMapOf()
  @Suppress("UNCHECKED_CAST")
  private fun <T> cache(name: String, value: T): T =
    cacheMap.getOrPut(name, { value as Any }) as T

  internal val traces: List<TraceType> get() = run {
    val argValue = variable("exo.trace.types", "exo_trace_types", "standard")
    cache(
      "exo.trace.types",
      if (argValue == "all")
        TraceType.values
      else
        argValue
          .split(",")
          .toList()
          .map { it.trim() }
          .flatMap { trace -> TraceType.values.filter { traceType -> trace == traceType.value } }
    )
  }

  fun tracesEnabled(tt: TraceType): Boolean =
    (traceEnabled && traces.contains(tt)) || tt == TraceType.Warning
}
