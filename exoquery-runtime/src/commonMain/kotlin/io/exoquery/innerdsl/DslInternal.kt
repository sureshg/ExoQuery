package io.exoquery.innerdsl

import io.exoquery.errorCap

sealed interface set
sealed interface setParams: set {
  fun excluding(vararg columns: Any): set = errorCap("The `excluding` expression of the Query was not inlined")
}
sealed interface SqlActionFilterable<Input, Output>
