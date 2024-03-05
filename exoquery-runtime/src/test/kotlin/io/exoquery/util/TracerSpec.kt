package io.exoquery.util

import io.exoquery.xr.DebugDump
import io.kotest.core.spec.style.FreeSpec

class TracerSpec :  FreeSpec({
  fun makeTracer(): Pair<Tracer.OutputSource.InMem, Tracer> =
    Tracer.OutputSource.InMem(DebugDump()).let { it ->
      it to Tracer(TraceType.Standard, TraceConfig(listOf(TraceType.Standard)), defaultIndent = 0, color = false, globalTracesEnabled = { true }, it)
    }

  "two-level-andReturn" {
    val (output, tracer) = makeTracer()
    val str = tracer("level1").andReturn {
      tracer("level2").andReturn {
        tracer("level3").andLog()
      }
    }
    println(output.sink.info)
  }

//  "two-level-andReturnIf" {
//    val (output, tracer) = makeTracer()
//    val str = tracer("level1").andReturnIf {
//      tracer("level2").andReturnIf {
//        tracer("level3").andLog()
//      }({ true })
//    }({ true })
//    println(output.value.toString())
//  }
})