package io.exoquery

class IllegalStructureError(msg: String) : Exception(msg)
class TransformXrError(msg: String, cause: Throwable?) : Exception(msg, cause) {
  constructor(msg: String): this(msg, null)
}

fun structError(msg: String): Nothing = throw IllegalStructureError(msg)
fun xrError(msg: String): Nothing = throw TransformXrError(msg)

class IllegalSqlOperation(msg: String, cause: Throwable?) : Exception(msg, cause) {
  constructor(msg: String): this(msg, null)
}

fun illegalOp(msg: String): Nothing = throw IllegalSqlOperation(msg)
