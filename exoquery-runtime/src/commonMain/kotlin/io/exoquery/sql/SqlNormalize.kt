package io.exoquery.sql

import io.exoquery.norm.Normalize
import io.exoquery.norm.RepropagateTypes
import io.exoquery.printing.HasPhasePrinting
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.*
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR

class SqlNormalize(
  val concatBehavior: ConcatBehavior = ConcatBehavior.AnsiConcat,
  val equalityBehavior: EqualityBehavior = EqualityBehavior.AnsiEquality,
  override val traceConf: TraceConfig = TraceConfig.empty,
  val disableApplyMap: Boolean = false
): HasPhasePrinting {
  override val traceType = TraceType.Normalizations
  override val trace by lazy { Tracer(traceType, traceConf, 1) }

  val NormalizePhase = Normalize(traceConf, disableApplyMap)
  val RepropagateTypesPhase = RepropagateTypes(traceConf)

  private val root = { it: Query -> it }
  private val normalize =
    root
      .andThen("RepropagateTypes") {
        RepropagateTypesPhase(it)
      }
      .andThen("Normalize") {
        NormalizePhase(it)
      }
  // TODO ExpandDistinct

  inline fun ((Query) -> Query).andThen(phaseTitle: String, crossinline f: (Query) -> Query): (Query) -> Query = { qRaw ->
    // Too much noise when both before and after the phase are printed
    demarcate("Beginning: ${phaseTitle}", qRaw)
    //demarcate(phaseTitle, q)
    val q = this(qRaw)
    val output = f(q)
    //demarcate("Completed: ${phaseTitle}", output)
    output
  }

  operator fun invoke(q: Query): Query = normalize(q)
}