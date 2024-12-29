package io.exoquery.plugin.transform

import io.exoquery.annotation.ChangeReciever
import io.exoquery.plugin.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class CallMethod(private val callerRaw: Caller, private val replacementFun: ReplacementMethodToCall, private val types: List<IrType>, private val tpe: IrType?) {
  context(BuilderContext) operator fun invoke(vararg args: IrExpression): IrExpression {
    val caller =
      when (replacementFun.callerType) {
        ChangeReciever.ToDispatch -> callerRaw.toDispatch()
        ChangeReciever.ToExtension -> callerRaw.toExtension()
        ChangeReciever.DoNothing -> callerRaw
      }

    val funName = replacementFun.methodToCall

    val invokeMethod =
      when (caller) {
        is Caller.Dispatch -> caller.reciver.type.findMethodOrFail(funName)
        is Caller.Extension ->  caller.reciver.type.findExtensionMethodOrFail(funName)
        is Caller.TopLevelMethod ->
          pluginCtx.referenceFunctions(CallableId(FqName(caller.packageName), Name.identifier(funName))).firstOrNull()?.let { MethodType.Method(it) }
            ?: throw IllegalArgumentException("Cannot find method `${funName}` in the package `${caller.packageName}`")
      }



    return when (invokeMethod) {
      is MethodType.Getter -> {
        if (args.isNotEmpty()) {
          throw IllegalArgumentException("Cannot call a getter with arguments but tried to call `${invokeMethod.sym.safeName}` with arguments: ${args.joinToString(", ") { it.dumpKotlinLike() }}")
        }
        with(builder) {
          when (caller) {
            is Caller.Dispatch -> {
              irCall(invokeMethod.sym, invokeMethod.sym.owner.returnType).apply {
                origin = IrStatementOrigin.GET_PROPERTY
                dispatchReceiver = caller.reciver
              }
            }
            else -> {
              error("Cannot call a getter from a non-dispatch receiver")
            }
          }
        }
      }
      is MethodType.Method -> {
        val invoke = invokeMethod.sym
        with(builder) {
          val invocation = if (tpe != null) irCall(invoke, tpe) else irCall(invoke)
          invocation.apply {
            when (caller) {
              is Caller.Dispatch -> {
                dispatchReceiver = caller.reciver
              }
              is Caller.Extension -> {
                extensionReceiver = caller.reciver
              }
              is Caller.TopLevelMethod -> {}
            }

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
  }
}

// TODO these should be implemented on Caller, not IrExpression

fun ReceiverCaller.call(name: ReplacementMethodToCall) = CallMethod(this, name, listOf(), null)
fun ReceiverCaller.callWithOutput(name: ReplacementMethodToCall, fullOutputType: IrType) = CallMethod(this, name, listOf(), fullOutputType)

fun ReceiverCaller.callWithParams(name: ReplacementMethodToCall, typeParams: List<IrType>): CallMethod = CallMethod(this, name, typeParams, null)
fun ReceiverCaller.callWithParamsAndOutput(name: ReplacementMethodToCall, typeParams: List<IrType>, fullOutputType: IrType): CallMethod = CallMethod(this, name, typeParams, fullOutputType)



fun ReceiverCaller.call(method: String) = CallMethod(this, ReplacementMethodToCall(method), listOf(), null)
fun ReceiverCaller.callWithOutput(method: String, fullOutputType: IrType) = CallMethod(this, ReplacementMethodToCall(method), listOf(), fullOutputType)

fun ReceiverCaller.callWithParams(method: String, typeParams: List<IrType>): CallMethod = CallMethod(this, ReplacementMethodToCall(method), typeParams, null)
fun ReceiverCaller.callWithParamsAndOutput(method: String, typeParams: List<IrType>, fullOutputType: IrType): CallMethod = CallMethod(this, ReplacementMethodToCall(method), typeParams, fullOutputType)

fun call(fullPathMethod: String): CallMethod {
  val split = fullPathMethod.split('.')
  val method = split.takeLast(1).firstOrNull() ?: throw IllegalArgumentException("No method to call inthe path: $fullPathMethod")
  val pack = split.dropLast(1)
  return call(pack.joinToString("."), method)
}
fun call(packageName: String, method: String) = CallMethod(Caller.TopLevelMethod(packageName), ReplacementMethodToCall(method), listOf(), null)
fun callWithOutput(packageName: String, method: String, tpe: IrType) = CallMethod(Caller.TopLevelMethod(packageName), ReplacementMethodToCall(method), listOf(), tpe)

fun callWithParams(packageName: String, method: String, typeParams: List<IrType>) = CallMethod(Caller.TopLevelMethod(packageName), ReplacementMethodToCall(method), typeParams, null)
fun callWithParamsAndOutput(packageName: String, method: String, typeParams: List<IrType>, tpe: IrType) = CallMethod(Caller.TopLevelMethod(packageName), ReplacementMethodToCall(method), typeParams, tpe)


fun IrExpression.callDispatch(method: String) = CallMethod(Caller.Dispatch(this), ReplacementMethodToCall(method), listOf(), null)
fun IrExpression.callDispatchWithOutput(method: String, fullOutputType: IrType) = CallMethod(Caller.Dispatch(this), ReplacementMethodToCall(method), listOf(), fullOutputType)
fun IrExpression.callDispatchWithParams(method: String, typeParams: List<IrType>): CallMethod = CallMethod(Caller.Dispatch(this), ReplacementMethodToCall(method), typeParams, null)
fun IrExpression.callDispatchWithParamsAndOutput(method: String, typeParams: List<IrType>, fullOutputType: IrType): CallMethod = CallMethod(Caller.Dispatch(this), ReplacementMethodToCall(method), typeParams, fullOutputType)


context (BuilderContext) fun createLambda0(functionBody: IrExpression, functionParent: IrDeclarationParent): IrFunctionExpression =
  with(builder) {
    val functionClosure = createLambda0Closure(functionBody, functionParent)
    val functionType = pluginCtx.symbols.functionN(0).typeWith(functionClosure.returnType)
    IrFunctionExpressionImpl(startOffset, endOffset, functionType, functionClosure, IrStatementOrigin.LAMBDA)
  }

context (BuilderContext) fun createLambda0Closure(functionBody: IrExpression, functionParent: IrDeclarationParent): IrSimpleFunction {
  return with(pluginCtx) {
    irFactory.buildFun {
      origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
      name = SpecialNames.NO_NAME_PROVIDED
      visibility = DescriptorVisibilities.LOCAL
      returnType = functionBody.type
      modality = Modality.FINAL
      isSuspend = false
    }.apply {
      parent = functionParent
      /*
      VERY important here to create a new irBuilder from the symbol i.e. createIrBuilder because
      the return-point needs to be the caller-function (which kotlin gets from the irBuilder).
      If the builder in the BuilderContext is used it will return back to whatever context the
      TransformInterpolatorInvoke IrCall expression is coming from (and this will be a non-local return)
      and since the return-type is wrong it will fail with a very large error that ultimately says:
      RETURN: Incompatible return type
       */
      body = pluginCtx.createIrBuilder(symbol).run {
        // don't use expr body, coroutine codegen can't generate for it.
        irBlockBody {
          +irReturn(functionBody)
        }
      }
    }
  }
}

fun IrPluginContext.createIrBuilder(
  symbol: IrSymbol,
  startOffset: Int = UNDEFINED_OFFSET,
  endOffset: Int = UNDEFINED_OFFSET,
) = DeclarationIrBuilder(this, symbol, startOffset, endOffset)
