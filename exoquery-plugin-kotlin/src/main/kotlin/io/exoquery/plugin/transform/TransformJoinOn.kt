package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.Lambda1Expression
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.safeName
import io.exoquery.plugin.trees.*
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.addArguments

class TransformJoinOn(override val ctx: TransformerOrigin): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    ExtractorsDomain.Call.`join-on(expr)`.matchesMethod(expression)

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall, superTransformer: io.exoquery.plugin.CaptureTransformer): IrExpression {
    val (caller, funExpression, params, blockBody) =
      on(expression).match(
        case(ExtractorsDomain.Call.`join-on(expr)`[Is()]).then { queryCallData -> queryCallData }
      ) ?: parseFail("Illegal block on function:\n${Messages.PrintingMessage(expression)}")

    //error("-------- Function:\n${funExpression.function.dumpSimple()}\n------------Params: ${funExpression.function.extensionReceiverParameter?.symbol?.safeName}")

     //There actually IS a reciver to this function and it should be named $this$on
    val reciverParam = funExpression.function.extensionReceiverParameter ?: illegalStruct("Extension Reciever for on-clause was null")
    val reciverSymbol = reciverParam.symbol.safeName
    val paramIdent = run {
      val tpe = TypeParser.parse(reciverParam.type)
      XR.Ident(reciverSymbol, tpe)
    }

    // parse the `on` clause of the join.on(...)
    val (onLambdaBody, bindsAccum) =
      with(makeParserContext().copy(internalVars + ScopeSymbols(listOf(reciverParam.symbol)))) {
        Parser.parseFunctionBlockBody(blockBody)
      }

    val onLambda = Lambda1Expression(XR.Function1(paramIdent, onLambdaBody))
    val onLambdaExpr = makeLifter().liftExpression(onLambda)
    // To transform the TableQuery etc... in the join(<Heree>).on clause before the `on`
    // No scope symbols into caller since it comes Before the on-clause i.e. before any symbols could be created
    val newCaller = caller.transform(superTransformer, internalVars)

    warn("------------ Binds Accum -----------\n" + bindsAccum.show())

    val bindsList = bindsAccum.makeDynamicBindsIr()

    return newCaller.callMethod("onExpr").invoke(onLambdaExpr, bindsList)
  }
}