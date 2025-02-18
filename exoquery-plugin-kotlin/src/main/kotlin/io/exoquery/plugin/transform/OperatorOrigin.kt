package io.exoquery.plugin.transform

import io.exoquery.xr.*
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.name.FqName
import kotlin.reflect.KClass

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
      OP.not,
      OP.minus
    ).map { it -> it.symbolName to it }.toMap()

  val UnaryOperator.symbolName: String get() =
    when (this) {
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
      OP.div -> "div"
      OP.gt -> "greater"
      OP.gte -> "greaterOrEqual"
      OP.lt -> "less"
      OP.lte -> "lessOrEqual"
      OP.minus -> "minus"
      OP.mod -> "rem"
      OP.mult -> "times"
      OP.plus -> "plus"
  }
}
