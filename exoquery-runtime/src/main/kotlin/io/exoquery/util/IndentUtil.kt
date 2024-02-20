package io.exoquery.util

interface IndentOps {
  val String.fitsOnOneLine: Boolean get() = !this.contains("\n")
  fun String.multiline(indent: Int, prefix: String): String =
    this.split("\n").map {elem -> indent.prefix + prefix + elem }.joinToString("\n")

  val Int.prefix: String get() = indentOf(this)

  private fun indentOf(num: Int): String = "  ".repeat(num)
}

fun List<String>.mkString() = this.joinToString("")
