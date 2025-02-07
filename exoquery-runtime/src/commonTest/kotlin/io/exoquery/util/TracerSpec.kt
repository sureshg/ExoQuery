//package io.exoquery.util
//
//import io.kotest.core.spec.style.FreeSpec
//
// TODO re-enable using the new debug-dumping
//class TracerSpec :  FreeSpec({
//  fun makeTracer(): Pair<Tracer.OutputSink.InMem, Tracer> =
//    Tracer.OutputSink.InMem(DebugDump()).let { it ->
//      it to Tracer(TraceType.Standard, TraceConfig(listOf(TraceType.Standard)), defaultIndent = 0, color = false, globalTracesEnabled = { true }, it)
//    }
//
//  "two-level-andReturn" {
//    val (output, tracer) = makeTracer()
//    val str = tracer("level1").andReturn {
//      tracer("level2").andReturn {
//        tracer("level3").andLog()
//      }
//    }
//    println(output.sink.info)
//  }
//
////  "two-level-andReturnIf" {
////    val (output, tracer) = makeTracer()
////    val str = tracer("level1").andReturnIf {
////      tracer("level2").andReturnIf {
////        tracer("level3").andLog()
////      }({ true })
////    }({ true })
////    println(output.value.toString())
////  }
//})
