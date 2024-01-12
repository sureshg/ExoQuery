package io.exoquery.plugin.trees

import io.exoquery.xr.*

sealed interface TypeBehavior {
  object SubstituteSubtypes: TypeBehavior
  object ReplaceWithReduction: TypeBehavior
}

sealed interface EmptyProductQuatBehavior {
  object Fail: EmptyProductQuatBehavior
  object Ignore: EmptyProductQuatBehavior
}

// I think beta reduction should be used for XR.Expression in all the cases, shuold
// move forward and find out if this is actually the case. If it is not, can probably
// just add `case ast if map.contains(ast) =>` to the apply for Query, Function, etc...
// maybe should have separate maps for Query, Function, etc... for that reason if those cases even exist
data class BetaReduction(val map: Map<XR.Expression, XR.Expression>, val typeBehavior: TypeBehavior, val emptyBehavior: EmptyProductQuatBehavior):
  StatelessTransformer {

  private fun replaceWithReduction() = typeBehavior == TypeBehavior.ReplaceWithReduction

  private fun BetaReduce(map: Map<XR.Expression, XR.Expression>) =
    BetaReduction(map, typeBehavior, emptyBehavior)

  private fun correctTheTypeOfReplacement(orig: XR.Expression, rep: XR.Expression) =
    when {
      replaceWithReduction() -> rep
      rep is XR.Labels.Terminal && rep.isBottomTypedTerminal() -> rep.withType(orig.type)
      rep is XR.Labels.Terminal -> {
        val type = orig.type.leastUpperType(rep.type) ?: run {
          // TODO log warning about the invalid reduction
          XRType.Unknown
        }
        rep.withType(type)
      }
      else -> rep
    }

  override fun apply(xr: XR.Expression): XR.Expression {
    val replacement = map[xr]
    return when {
      // I.e. we have an actual replacement for this element
      replacement != null -> {
        val rep = BetaReduce(map - xr - replacement).apply(replacement)
        correctTheTypeOfReplacement(xr, rep)
      }

      //xr is Property && xr.of is Product ->


      else -> super.apply(xr)
    }
  }


}