package io.exoquery

import io.exoquery.annotation.DslExt

@DslExt
fun <R> printSource(f: () -> R): String = errorCap("Compile time plugin did not transform the tree")
fun printSourceExpr(input: String): String = input

@DslExt
fun currentSourceFile(): String = errorCap("Compile time plugin did not transform the tree")
