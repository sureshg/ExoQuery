package io.exoquery.util

actual fun defaultTraceOutputSink(path: String): Tracer.OutputSink =
  Tracer.OutputSink.None
