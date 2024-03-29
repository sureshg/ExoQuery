package io.exoquery.xr

import io.exoquery.pprint
import io.exoquery.util.dropLastSegment
import io.exoquery.util.takeLastSegment

object Good {
  sealed interface Str {
    fun matches(str: String): Boolean

    object Any: Str { override fun matches(str: String) = true }
    // TODO implement a regex version?
    data class Actual(val value: String): Str {  override fun matches(str: String) = value == str  }
  }
  sealed interface FQN {
    fun matches(fqn: XR.FqName): Boolean

    companion object {
      operator fun invoke(fullPath: String) = FQN.Actual(fullPath)
    }

    // The path is optional but at least the name needs to match
    data class Actual(val path: Good.Str, val name: Good.Str.Actual): FQN {
      companion object {
        operator fun invoke(fullPath: String) =
          Good.FQN.Actual(Str.Actual(fullPath.dropLastSegment()), Str.Actual(fullPath.takeLastSegment()))
      }
      override fun matches(xrFQN: XR.FqName) = path.matches(xrFQN.path) && name.matches(xrFQN.name)
    }
    object Any {
      fun matches(fqn: XR.FqName) = true
    }
  }



  data class MethodCall(val name: Good.FQN.Actual, val originalType: Good.FQN) {
    fun matches(call: XR.MethodCallName) =
      name.matches(call.name) && originalType.matches(call.originalType)
  }
  data class GlobalCall(val name: Good.FQN.Actual) {
    fun matches(call: XR.FqName) =
      name.matches(call)
  }
}

data class MethodWhitelist(val methodCalls: List<Good.MethodCall>, val globalCalls: List<Good.GlobalCall>) {
  companion object {
    val default =
      MethodWhitelist(
        listOf(
          Good.MethodCall(Good.FQN.Actual("kotlin.text.split"), Good.FQN.Actual("kotlin.String"))
        ),
        listOf()
      )
  }


  fun containsMethod(methodName: String) =
    methodCalls.any { it.name.name.matches(methodName) } || globalCalls.any { it.name.name.matches(methodName) }

  fun contains(methodCall: XR.MethodCallName) = methodCalls.any { it.matches(methodCall) }
  fun contains(globalCall: XR.FqName) = globalCalls.any { it.matches(globalCall) }
}

/** Only a string in here for now but we might want to add more */
data class SqlName(val value: String)

/**
 * Since we want to have a different allowable-methods list in the parser than in the dialects
 * (since generally the parser should accept more kinds of things because it is a 1st-level check)
 * that should be separate from the method map. Note that if needed, it should be possible to produce
 * a MethodWhiteList from a MethodMapping.
 */
data class MethodMapping(val methodCallMapping: Map<Good.MethodCall, SqlName>, val globalCallMapping: Map<Good.GlobalCall, SqlName>) {
  companion object {
    val default =
      MethodMapping(
        mapOf(Good.MethodCall(Good.FQN("kotlin.text.split"), Good.FQN("kotlin.String")) to SqlName("split")),
        mapOf()
      )
  }

  operator fun get(methodCall: XR.MethodCallName) = methodCallMapping.entries.firstOrNull { (k, _) -> k.matches(methodCall) }?.value
  operator fun get(globalCall: XR.FqName) = globalCallMapping.entries.firstOrNull { (k, _) -> k.matches(globalCall) }?.value
}

fun main() {
  val name = XR.MethodCallName(XR.FqName("kotlin.text.split"), XR.FqName("kotlin.String"))
  println(pprint(name))
}
