package io.exoquery.plugin.transform

import org.jetbrains.kotlin.ir.expressions.IrExpression

sealed interface ReciverCaller: Caller
sealed interface Caller {
  data class DispatchReciver(val reciver: IrExpression): ReciverCaller
  data class ExtensionReciver(val reciver: IrExpression): ReciverCaller
  data class TopLevelMethod(val packageName: String): Caller
}