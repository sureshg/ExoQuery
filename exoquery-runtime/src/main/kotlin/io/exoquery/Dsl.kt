package io.exoquery

import io.exoquery.annotation.Captured


fun <T> capture(block: () -> T): @Captured("initial-value") SqlExpression<T> = error("Compile time plugin did not transform the tree")
