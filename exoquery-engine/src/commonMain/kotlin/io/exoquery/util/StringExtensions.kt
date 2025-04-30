package io.exoquery.util

fun String.dropLastSegment() =
  this.dropLastWhile { it != '.' }.dropLastWhile { it == '.' }

fun String.takeLastSegment() =
  this.takeLastWhile { it != '.' }.dropLastWhile { it == '.' }
