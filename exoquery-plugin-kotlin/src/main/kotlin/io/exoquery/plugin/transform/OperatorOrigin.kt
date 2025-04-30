package io.exoquery.plugin.transform

import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.OP
import io.exoquery.xr.UnaryOperator
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin

// Possible implementation for a dynamic method-whitelist
//object Whitelist {
//  data class MethodCallEntry(val cls: FqName, val methods: String, val args: List<FqName>) {
//    companion object {
//      fun multi(cls: KClass<*>, methods: List<String>, args: List<KClass<*>>) =
//        methods.map { MethodCallEntry(cls, it, args) }
//    }
//  }
//
//  class GlobalCall(method: FqName, val args: List<KClass<*>>)
//
//  val methodCalls = buildList() {
//    add(MethodCallEntry(String::class, "toLowerCase", emptyList()))
//    add(MethodCallEntry(String::class, "toUpperCase", emptyList()))
//    addAll(MethodCallEntry.multi(String::class, listOf("toInt", "toLong", "toBoolean", "toFloat", "toDouble", "toString"), emptyList()))
//    addAll(MethodCallEntry.multi(Int::class, listOf("toInt", "toLong", "toBoolean", "toFloat", "toDouble", "toString"), emptyList()))
//    addAll(MethodCallEntry.multi(Long::class, listOf("toInt", "toLong", "toBoolean", "toFloat", "toDouble", "toString"), emptyList()))
//    addAll(MethodCallEntry.multi(Short::class, listOf("toInt", "toLong", "toBoolean", "toFloat", "toDouble", "toString"), emptyList()))
//    addAll(MethodCallEntry.multi(Boolean::class, listOf("toInt", "toLong", "toString"), emptyList()))
//    addAll(MethodCallEntry.multi(Float::class, listOf("toInt", "toLong", "toBoolean", "toFloat", "toDouble", "toString"), emptyList()))
//    addAll(MethodCallEntry.multi(Double::class, listOf("toInt", "toLong", "toBoolean", "toFloat", "toDouble", "toString"), emptyList()))
//  }
//
//  private val methodCallClasses by lazy { methodCalls.groupBy { it.cls } }
//}

object UnaryOperators {
  val operators =
    listOf<UnaryOperator>(
//      StringOperator.toInt,
//      StringOperator.toLong,
//      StringOperator.toLowerCase,
//      StringOperator.toUpperCase,
      OP.Not,
      OP.Minus
    ).map { it -> it.symbolName to it }.toMap()

  val UnaryOperator.symbolName: String
    get() =
      when (this) {
//      StringOperator.toInt -> "toInt"
//      StringOperator.toLong -> "toLong"
//      StringOperator.toLowerCase -> "toLowerCase"
//      StringOperator.toUpperCase -> "toUpperCase"
        OP.Not -> "not"
        OP.Minus -> "unaryMinus"
      }
}

object BinaryOperators {
  val operators =
    listOf<BinaryOperator>(
      OP.StrPlus,
      OP.EqEq,
      OP.NotEq,
      OP.And,
      OP.Or,
      OP.Div,
      OP.Gt,
      OP.GtEq,
      OP.Lt,
      OP.LtEq,
      OP.Minus,
      OP.Mod,
      OP.Mult,
      OP.Plus,
//      StringOperator.split,
//      StringOperator.startsWith
    ).map { it -> it.symbolName to it }.toMap()

  val BinaryOperator.symbolName: String
    get() =
      when (this) {
        OP.StrPlus -> "plus"
        OP.EqEq -> IrStatementOrigin.EQEQ.debugName
        OP.NotEq -> IrStatementOrigin.EXCLEQ.debugName // EXCEL == exclamation point
        OP.And -> IrStatementOrigin.ANDAND.debugName
        OP.Or -> IrStatementOrigin.OROR.debugName
        OP.Div -> "div"
        OP.Gt -> "greater"
        OP.GtEq -> "greaterOrEqual"
        OP.Lt -> "less"
        OP.LtEq -> "lessOrEqual"
        OP.Minus -> "minus"
        OP.Mod -> "rem"
        OP.Mult -> "times"
        OP.Plus -> "plus"
      }
}
