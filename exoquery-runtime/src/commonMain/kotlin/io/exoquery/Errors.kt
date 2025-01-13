package io.exoquery

class IllegalStructureError(msg: String) : Exception(msg)
class TransformXrError(msg: String) : Exception(msg)

fun structError(msg: String): Nothing = throw IllegalStructureError(msg)
fun xrError(msg: String): Nothing = throw TransformXrError(msg)
