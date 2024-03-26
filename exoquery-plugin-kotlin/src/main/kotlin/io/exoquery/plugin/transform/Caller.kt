package io.exoquery.plugin.transform

import io.decomat.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

import io.decomat.productComponentsOf as productOf
import io.decomat.HasProductClass as PC

sealed class ReceiverCaller(open val reciver: IrExpression): Caller, PC<ReceiverCaller> {
  override val productComponents by lazy { productOf(this, reciver) }

  companion object {
    operator fun <AP: Pattern<A>, A: IrExpression> get(value: AP) =
      customPattern1(value) { it: ReceiverCaller ->
        Components1(it.reciver)
      }
  }

  fun <D> transform(transformer: IrElementTransformer<D>, data: D) =
    when (this) {
      is Caller.DispatchReceiver -> Caller.DispatchReceiver(reciver.transform(transformer, data))
      is Caller.ExtensionReceiver -> Caller.ExtensionReceiver(reciver.transform(transformer, data))
    }
}
sealed interface Caller {
  data class DispatchReceiver(override val reciver: IrExpression): ReceiverCaller(reciver)
  data class ExtensionReceiver(override val reciver: IrExpression): ReceiverCaller(reciver)
  data class TopLevelMethod(val packageName: String): Caller
}
