package io.exoquery.plugin.transform

import io.exoquery.plugin.findMethodOrFail
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType

class CallMethod(private val host: IrExpression, private val funName: String, private val tpe: IrType?) {
  context(BuilderContext) operator fun invoke(vararg args: IrExpression): IrExpression {
    val lambdaInvoke = host.type.findMethodOrFail(funName)
    return with (builder) {
      val invocation = if (tpe != null) irCall(lambdaInvoke, tpe) else irCall(lambdaInvoke)
      invocation.apply {
        dispatchReceiver = host
        for ((index, expr) in args.withIndex()) {
          putValueArgument(index, expr)
        }
      }
    }
  }
}

fun IrExpression.callMethod(name: String) = CallMethod(this, name, null)
fun IrExpression.callMethodWithType(name: String, tpe: IrType) = CallMethod(this, name, tpe)


class CallMethodTypedArgs(private val host: IrExpression, private val funName: String, private val tpe: IrType?) {
  operator fun invoke(vararg types: IrType): CallMethodTyped =
    CallMethodTyped(host, funName, types.toList(), tpe)
}

class CallMethodTyped(private val host: IrExpression, private val funName: String, private val types: List<IrType>, private val tpe: IrType?) {
  context(BuilderContext) operator fun invoke(vararg args: IrExpression): IrExpression {
    val lambdaInvoke = host.type.findMethodOrFail(funName)
    return with (builder) {
      val invocation = if (tpe != null) irCall(lambdaInvoke, tpe) else irCall(lambdaInvoke)
      invocation.apply {
        dispatchReceiver = host
        for ((index, tpe) in types.withIndex()) {
          putTypeArgument(index, tpe)
        }
        for ((index, expr) in args.withIndex()) {
          putValueArgument(index, expr)
        }
      }
    }
  }
}

fun IrExpression.callMethodTyped(name: String): CallMethodTypedArgs = CallMethodTypedArgs(this, name, null)
fun IrExpression.callMethodTypedWithType(name: String, tpe: IrType): CallMethodTypedArgs = CallMethodTypedArgs(this, name, tpe)
