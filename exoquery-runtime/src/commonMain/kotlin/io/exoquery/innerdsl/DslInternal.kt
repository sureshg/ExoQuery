package io.exoquery.innerdsl

import io.exoquery.errorCap

// TODO figure out how to type this the same way as the INSERT clause which it must be, i.e. the setParams case
sealed interface set<T>
sealed interface setParams<T>: set<T> {
  fun excluding(vararg columns: Any): set<T> = errorCap("The `excluding` expression of the Query was not inlined")
}
sealed interface SqlActionFilterable<Input, Output>
