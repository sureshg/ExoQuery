package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.Lambda1Expression
import io.exoquery.SqlVariable
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.safeName
import io.exoquery.plugin.trees.*
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import io.exoquery.plugin.trees.ExtractorsDomain.Call.`join-on(expr)`
import io.exoquery.xr.XRType
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments

class TransformJoinOn(override val ctx: TransformerOrigin): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    `join-on(expr)`.matchesMethod(expression)

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall, superTransformer: io.exoquery.plugin.CaptureTransformer): IrExpression {
    val (caller, funExpression, params, blockBody) =
      on(expression).match(
        case(`join-on(expr)`[Is()]).then { queryCallData -> queryCallData }
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
    val onLambdaBody =
      with(makeParserContext().copy(internalVars + ScopeSymbols(listOf(reciverParam.symbol)))) {
        Parser.parseFunctionBlockBody(blockBody)
      }

    val onLambda = Lambda1Expression(XR.Function1(paramIdent, onLambdaBody))
    val onLambdaExpr = lifter.liftExpression(onLambda)
    // To transform the TableQuery etc... in the join(<Heree>).on clause before the `on`
    // No scope symbols into caller since it comes Before the on-clause i.e. before any symbols could be created
    val newCaller = caller.transform(superTransformer, internalVars)

    return newCaller.callMethod("onExpr").invoke(onLambdaExpr)
  }
}

class TransformQueryFlatMap(override val ctx: TransformerOrigin, val replacementMethod: String): Transformer() {
  private val matcher = ExtractorsDomain.Call.QueryFlatMap

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    matcher.matchesMethod(expression)


  private fun onlyOneParam(params: List<IrValueParameter>): IrValueParameter =
    if (params.size != 1)
      parseFail("Number of val-parameters needs to be one")
    else
      params.first()

  // in a flatMap not running the parser, just looking that the symbols used
  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall, superTransformer: io.exoquery.plugin.CaptureTransformer): IrExpression {
    val (caller, funExpression, params, blockBody) =
      on(expression).match(
        case(matcher[Is()]).then { queryCallData -> queryCallData }
      ) ?: parseFail("Illegal block on function:\n${Messages.PrintingMessage(expression)}")

    // TODO Needs to convey SourceLocation coordinates, look into the 'warn/error' thing to see how to do that
    //      also, should generally just return the expression instead of throwing exceptions in the compiler
    //      perhaps use some kind of FP either-like structure here???

    val lambdaArg = onlyOneParam(params)

    // flatMap( <p> /*this!*/ -> {...})
    val lambdaArgName = lambdaArg.name.asString()

    // flatMap( (p: <Person> /*this!*/) -> {...})
    val lambdaArgType = lambdaArg.type

    // Transform the contents of the lambda e.g:
    // x.flatMap { p -> y.map(...) }    into:
    //   x.flatMap { p -> y.mapExpr(...) }
    val newFunExpression = funExpression.transform(superTransformer, ctx.parentScopeSymbols)
    // (p:class).invoke(...)

    val applyLambda =
      // TODO Add a type for R?
      newFunExpression.callMethod("invoke")(
        lifter.liftSqlVariableWithType(SqlVariable.new<Any>(lambdaArgName), lambdaArgType.simpleTypeArgs.first())
      )

    val lambdaParamIdent = XR.Ident(lambdaArgName, TypeParser.parse(lambdaArgType))

    // Transform the caller of the expression e.g:
    // TableQuery<Person>.flatMap { ... }   into:
    //   TableQuery<Person>.fromExpr(...)
    val newCaller = caller.transform(superTransformer, ctx.parentScopeSymbols)

    // e.g. query.callMethod("flatMap")(Ident(x), { x -> x.name /*XR*/ }(Ident(x))
    val outputCall = newCaller.callMethod(replacementMethod)(lifter.liftXR(lambdaParamIdent), applyLambda)
    return outputCall
  }

}