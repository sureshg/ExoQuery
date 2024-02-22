package io.exoquery.util

val String.fitsOnOneLine: Boolean get() = !this.contains("\n")
fun String.multiline(indent: Int, prefix: String): String =
  this.split("\n").map {elem -> indent.prefix + prefix + elem }.joinToString("\n")

val Int.prefix: String get() = indentOf(this)

internal fun indentOf(num: Int): String = "  ".repeat(num)

fun List<String>.mkString() = this.joinToString("")

fun <T> List<T>.mkString() = this.joinToString("")
