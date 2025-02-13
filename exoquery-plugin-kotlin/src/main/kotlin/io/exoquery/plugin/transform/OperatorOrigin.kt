package io.exoquery.plugin.transform

import io.exoquery.xr.*
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin

/*
AggregationOperator.avg -> "avg"
AggregationOperator.max -> "max"
AggregationOperator.min -> "min"
AggregationOperator.size -> "size"
AggregationOperator.sum -> "sum"

 */

object UnaryOperators {
  val operators =
    listOf<UnaryOperator>(
      OP.isEmpty,
      OP.nonEmpty,
//      StringOperator.toInt,
//      StringOperator.toLong,
//      StringOperator.toLowerCase,
//      StringOperator.toUpperCase,
      OP.not,
      OP.minus
    ).map { it -> it.symbolName to it }.toMap()

  val UnaryOperator.symbolName: String get() =
    when (this) {
      OP.isEmpty -> "isEmpty"
      OP.nonEmpty -> "nonEmpty"
//      StringOperator.toInt -> "toInt"
//      StringOperator.toLong -> "toLong"
//      StringOperator.toLowerCase -> "toLowerCase"
//      StringOperator.toUpperCase -> "toUpperCase"
      OP.not -> "not"
      OP.minus -> "unaryMinus"
    }
}

object BinaryOperators {
  val operators =
    listOf<BinaryOperator>(
      OP.strPlus,
      OP.`==`,
      OP.`!=`,
      OP.and,
      OP.or,
      OP.contains,
      OP.div,
      OP.gt,
      OP.gte,
      OP.lt,
      OP.lte,
      OP.minus,
      OP.mod,
      OP.mult,
      OP.plus,
//      StringOperator.split,
//      StringOperator.startsWith
    ).map { it -> it.symbolName to it }.toMap()

  val BinaryOperator.symbolName: String get() =
    when (this) {
      OP.strPlus -> "plus"
      OP.`==` -> IrStatementOrigin.EQEQ.debugName
      OP.`!=` -> IrStatementOrigin.EXCLEQ.debugName // EXCEL == exclamation point
      OP.and -> IrStatementOrigin.ANDAND.debugName
      OP.or -> IrStatementOrigin.OROR.debugName
      OP.contains -> IrStatementOrigin.IN.debugName
      OP.div -> "div"
      OP.gt -> "greater"
      OP.gte -> "greaterOrEqual"
      OP.lt -> "less"
      OP.lte -> "lessOrEqual"
      OP.minus -> "minus"
      OP.mod -> "rem"
      OP.mult -> "times"
      OP.plus -> "plus"
//      StringOperator.split -> "split"
//      StringOperator.startsWith -> "startsWith"
  }
}
