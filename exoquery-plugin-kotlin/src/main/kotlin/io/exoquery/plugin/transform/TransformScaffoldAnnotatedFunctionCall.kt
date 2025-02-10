package io.exoquery.plugin.transform

import io.exoquery.annotation.CapturedFunction
import io.exoquery.plugin.hasAnnotation
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.simpleValueArgs
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike


context(LocationContext, BuilderContext, CompileLogger)
fun IrCall.zeroisedArgs(): IrCall {
  val call = this
  return with (builder) {
    val newCall = irCall(call.symbol)
    call.dispatchReceiver?.let { newCall.dispatchReceiver = it }
    call.extensionReceiver?.let { newCall.extensionReceiver = it }
    newCall.typeArguments.withIndex().forEach { (i, tpe) -> putTypeArgument(i, tpe) }
    // no value arguments, should not have any since they are added to the scaffolding
    newCall
  }
}

context(LocationContext, BuilderContext, CompileLogger)
fun buildScaffolding(zeroisedCall: IrCall, originalArgs: List<IrExpression?>): IrExpression {
  val args = listOf(zeroisedCall) + originalArgs
  return call("io.exoquery.util.scaffoldCapFunctionQuery").invoke(*args.toTypedArray())
}


class TransformScaffoldAnnotatedFunctionCall(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(call: IrCall): Boolean =
    call.symbol.owner is IrSimpleFunction && call.symbol.owner.hasAnnotation<CapturedFunction>()


  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(call: IrCall): IrExpression {
    val originalArgs = call.simpleValueArgs
    val zeroizedCallRaw = call.zeroisedArgs()

    // Need to project the call to make it uprootable in the paresr in later stages. For example
    //...
    //val zeroizedCall = (TransformProjectCapture(ctx, superTransformer).transform(zeroizedCallRaw) ?: zeroizedCallRaw) as IrCall
    val zeroizedCall = zeroizedCallRaw as IrCall

    val scaffoldedCall = buildScaffolding(zeroizedCall, originalArgs)
    //error("""
    //  |--------------------------- Scaffolded call: ---------------------------
    //  |${scaffoldedCall.dumpKotlinLike()}
    //""".trimMargin())

    return scaffoldedCall
  }

}
