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
      SetOperator.isEmpty,
      SetOperator.nonEmpty,
//      StringOperator.toInt,
//      StringOperator.toLong,
//      StringOperator.toLowerCase,
//      StringOperator.toUpperCase,
      BooleanOperator.not,
      NumericOperator.minus
    ).map { it -> it.symbolName to it }.toMap()

  val UnaryOperator.symbolName: String get() =
    when (this) {
      SetOperator.isEmpty -> "isEmpty"
      SetOperator.nonEmpty -> "nonEmpty"
//      StringOperator.toInt -> "toInt"
//      StringOperator.toLong -> "toLong"
//      StringOperator.toLowerCase -> "toLowerCase"
//      StringOperator.toUpperCase -> "toUpperCase"
      BooleanOperator.not -> "not"
      NumericOperator.minus -> "unaryMinus"
    }
}

object BinaryOperators {
  val operators =
    listOf<BinaryOperator>(
      StringOperator.`+`,
      EqualityOperator.`==`,
      EqualityOperator.`!=`,
      BooleanOperator.and,
      BooleanOperator.or,
      SetOperator.contains,
      NumericOperator.div,
      NumericOperator.gt,
      NumericOperator.gte,
      NumericOperator.lt,
      NumericOperator.lte,
      NumericOperator.minus,
      NumericOperator.mod,
      NumericOperator.mult,
      NumericOperator.plus,
//      StringOperator.split,
//      StringOperator.startsWith
    ).map { it -> it.symbolName to it }.toMap()

  val BinaryOperator.symbolName: String get() =
    when (this) {
      StringOperator.`+` -> "plus"
      EqualityOperator.`==` -> IrStatementOrigin.EQEQ.debugName
      EqualityOperator.`!=` -> IrStatementOrigin.EXCLEQ.debugName // EXCEL == exclamation point
      BooleanOperator.and -> IrStatementOrigin.ANDAND.debugName
      BooleanOperator.or -> IrStatementOrigin.OROR.debugName
      SetOperator.contains -> IrStatementOrigin.IN.debugName
      NumericOperator.div -> "div"
      NumericOperator.gt -> "greater"
      NumericOperator.gte -> "greaterOrEqual"
      NumericOperator.lt -> "less"
      NumericOperator.lte -> "lessOrEqual"
      NumericOperator.minus -> "minus"
      NumericOperator.mod -> "rem"
      NumericOperator.mult -> "times"
      NumericOperator.plus -> "plus"
//      StringOperator.split -> "split"
//      StringOperator.startsWith -> "startsWith"
  }
}
