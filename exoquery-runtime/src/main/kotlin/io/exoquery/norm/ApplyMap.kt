package io.exoquery.norm

import io.decomat.*
import io.exoquery.util.TraceConfig
import io.exoquery.util.TraceType
import io.exoquery.util.Tracer
import io.exoquery.xr.CollectXR
import io.exoquery.xr.XR
import io.exoquery.xr.XR.*
import io.exoquery.xr.*

class ApplyMap(val traceConfig: TraceConfig) {

  val trace: Tracer =
    Tracer(TraceType.AvoidAliasConflict, traceConfig, 1)

  companion object {
    fun Expression.hasImpureInfix() = // TODO Optimize with ContainsXR
      CollectXR.invoke(this) {
        when {
          this is Infix && this.pure == false -> this
          else -> null
        }
      }.isNotEmpty()

    fun Expression.hasImpurities() = // TODO Optimize with ContainsXR
      CollectXR.invoke(this) {
        when {
          this is Infix && this.pure == false -> this
          this is Aggregation -> this
          else -> null
        }
      }.isNotEmpty()

    val XR.Map.Companion.DistinctHead get() = DistinctHeadMatch()
    class DistinctHeadMatch() {
      operator fun <AP: Pattern<XR.Query>, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
        customPattern2M(x, y) { it: XR.Map ->
          with(it) {
            when {
              this.head is Distinct -> Components2M(this.head.head, this.id, this.body)
              else -> null
            }
          }
        }
    }
  }

  object DetachableMap {
  operator fun <AP: Pattern<XR.Query>, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
      customPattern2M(x, y) { it: XR.Query ->
        with(it) {
          when {
            this is XR.Map && body.hasImpurities() -> null
            this is XR.Map && head is DistinctOn -> null
            this is XR.Map && head is FlatJoin -> null
            this is XR.Map && body.hasImpureInfix() -> null
            this is XR.Map -> Components2M(head, id, body)
            else -> null
          }
        }
      }
  }

// Scala
//  object DetachableMap {
//    def unapply(ast: Ast): Option[(Ast, Ident, Ast)] =
//      ast match {
//        // Maps that contains
//        case Map(_, _, HasImpurities())    => None
//        case Map(a: DistinctOn, b, c)           => None
//        case Map(a: FlatJoin, b, c)             => None // FlatJoin should always be surrounded by a Map
//        case Map(a, b, InfixedTailOperation(c)) => None
//        case Map(a, b, c)                       => Some((a, b, c))
//        case _                                  => None
//      }
//  }

  fun foo() {

  }

  fun invoke(q: Query): Query? =
    on(q).match(
      case(XR.Map.DistinctHead[DetachableMap[Is(), Is()], Is()]).then { (a, b, c), d, e ->
        trace("ApplyMap on Distinct for $q") andReturn { Distinct(XR.Map(a, b, c)) }
      }
    )

  /*
  case Map(Distinct(DetachableMap(a, b, c)), d, e) if isomorphic(e, c, d) =>
        trace"ApplyMap on Distinct for $q" andReturn Some(Distinct(Map(a, b, c)))
   */

}