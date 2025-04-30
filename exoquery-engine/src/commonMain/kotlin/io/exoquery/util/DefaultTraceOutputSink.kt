package io.exoquery.util

// function to help create this sink at compile time
expect fun defaultTraceOutputSink(path: String): Tracer.OutputSink
