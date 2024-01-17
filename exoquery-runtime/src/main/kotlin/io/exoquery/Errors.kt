package io.exoquery

class ParseError(msg: String) : Exception(msg)
class IllegalStructureError(msg: String) : Exception(msg)
class TransformXrError(msg: String) : Exception(msg)



fun parseError(msg: String): Nothing = throw ParseError(msg)
fun structError(msg: String): Nothing = throw IllegalStructureError(msg)
fun xrError(msg: String): Nothing = throw TransformXrError(msg)
