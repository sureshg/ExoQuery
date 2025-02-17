package io.exoquery

import io.exoquery.annotation.ExoExtras

@ExoExtras
fun <R> printSource(f: () -> R): String = errorCap("Compile time plugin did not transform the tree")
fun printSourceExpr(input: String): String = input

@ExoExtras
fun currentSourceFile(): String = errorCap("Compile time plugin did not transform the tree")
