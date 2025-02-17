package io.exoquery.plugin.transform

import io.exoquery.annotation.TracesEnabled
import io.exoquery.parseError
import io.exoquery.plugin.getAnnotation
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.source
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.simpleTypeArgs
import io.exoquery.plugin.varargValues
import io.exoquery.util.FilePrintOutputSink
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

// TODO also want to get TracesEnabled annotation from the build query itself.
object ComputeEngineTracing {
  context(LocationContext, BuilderContext, CompileLogger)
  private fun getTraceAnnotations() = run {
    // get annotations
    val traceTypesClsRef = currentFileRaw.getAnnotation<TracesEnabled>()?.valueArguments?.firstOrNull()?.varargValues() ?: emptyList()
    val traceTypesNames =
      traceTypesClsRef
        .map { ref -> (ref as? IrClassReference) ?: parseError("Invalid Trace Type: ${ref.source() ?: ref.dumpKotlinLike()} was not a class-reference:\n${ref.dumpSimple()}") }
        .mapNotNull { ref ->
          // it's TracesEnabled(KClass<TraceType.Normalizations>, etc...) so we need to take 1st argument from the type
          val shortName = ref.type.simpleTypeArgs.first().classFqName?.shortName()?.asString()
          shortName?.let { TraceType.Companion.fromClassStr(it) }
        }

    traceTypesNames
  }

  context(LocationContext, BuilderContext, CompileLogger)
  operator fun invoke() = run {
    val traceTypesNames = getTraceAnnotations()
    val writeSource =
      if (traceTypesNames.isNotEmpty())
        FilePrintOutputSink.open(options)
      else
        null
    val traceConfig = TraceConfig.Companion.empty.copy(traceTypesNames, writeSource ?: Tracer.OutputSink.None)
    traceConfig to writeSource
  }
}
