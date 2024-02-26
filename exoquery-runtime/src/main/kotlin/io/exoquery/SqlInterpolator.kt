package io.exoquery

interface InfixValue {
  fun <T> make(): T
  val asCondition: Boolean
  val pure: InfixValue
}

interface SqlInterpolator {
  // TODO better error message
  operator fun invoke(string: String): InfixValue = error("Should not be implemented")
}


