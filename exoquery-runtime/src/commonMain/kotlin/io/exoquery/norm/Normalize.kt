package io.exoquery.norm

import io.exoquery.printing.HasPhasePrinting
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.*
import io.exoquery.xr.XR.*
import io.exoquery.xr.XR

class Normalize(override val traceConf: TraceConfig, val disableApplyMap: Boolean): StatelessTransformer, HasPhasePrinting {

  override val traceType: TraceType = TraceType.Normalizations
  override val trace: Tracer = Tracer(traceType, traceConf, 1)

  val DealiasPhase by lazy { DealiasApply(traceConf) }
  val AvoidAliasConflictPhase by lazy { AvoidAliasConflictApply(traceConf) }
  val NormalizeNestedStructuresPhase by lazy { NormalizeNestedStructures(this) }
  val SymbolicReductionPhase by lazy { SymbolicReduction(traceConf) }
  val AdHocReductionPhase by lazy { AdHocReduction(traceConf) }
  val OrderTermsPhase by lazy { OrderTerms(traceConf) }

//  override def apply(q: Ast): Ast =
//    super.apply(BetaReduction(q))

  // Quill originally did this at the root-level. I think it is fine
  // to just do it for expressions because normalizations will propagate down to them anyway but I'm not 100% sure
  override fun invoke(xr: Expression): Expression =
    super.invoke(BetaReduction(xr))

//  override def apply(q: Action): Action =
//    NormalizeReturningPhase(super.apply(q))

  override operator fun invoke(q: Query): Query =
    trace("Avoid Capture and Normalize $q into:") andReturn {
      norm(DealiasPhase(AvoidAliasConflictPhase(q, false)))
      //norm(AvoidAliasConflictPhase(q, false))
    }

//  override def apply(q: Query): Query =
//    trace"Avoid Capture and Normalize $q into:" andReturn
//      norm(DealiasPhase(AvoidAliasConflict(q, false)))


  val applyMapInterp   = Tracer(TraceType.ApplyMap, traceConf, 1)
  val applyMapInstance = ApplyMap(traceConf)
  fun ApplyMapPhase(q: Query): Query? {
    // For logging that ApplyMap has been disabled
    return if (disableApplyMap) {
      applyMapInstance.trace("ApplyMap phase disabled. Not executing on: $q").andLog()
      null
    } else {
      applyMapInstance(q)
    }
  }

  private fun norm(q: Query): Query {
    fun normIfNew(qry: Query): Query? = if (qry != q) norm(qry) else null
    return NormalizeNestedStructuresPhase(q)?.let {
      demarcate("NormalizeNestedStructures", it)
      normIfNew(it)
    } ?: ApplyMapPhase(q)?.let {
      demarcate("ApplyMap", it)
      normIfNew(it)
    } ?: SymbolicReductionPhase(q)?.let {
      demarcate("SymbolicReduction", it)
      normIfNew(it)
    } ?: AdHocReductionPhase(q)?.let {
      demarcate("AdHocReduction", it)
      normIfNew(it)
    } ?: OrderTermsPhase(q)?.let {
      demarcate("OrderTerms", it)
      normIfNew(it)
    }
    ?: q
  }

//  @tailrec
//  private def norm(q: Query): Query =
//    q match {
//      case NormalizeNestedStructuresPhase(query) =>
//        demarcate("NormalizeNestedStructures")(query)
//        norm(query)
//      case ApplyMapPhase(query) =>
//        demarcate("ApplyMap")(query)
//        norm(query)
//      case SymbolicReductionPhase(query) =>
//        demarcate("SymbolicReduction")(query)
//        norm(query)
//      case AdHocReductionPhase(query) =>
//        demarcate("AdHocReduction")(query)
//        norm(query)
//      case OrderTermsPhase(query) =>
//        demarcate("OrderTerms")(query)
//        norm(query)
//      case other =>
//        other
//    }
}
