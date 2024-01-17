package io.exoquery.plugin.transform

import io.decomat.*
import io.exoquery.Lambda1Expression
import io.exoquery.xr.XR
import io.exoquery.plugin.*
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.CollectDecls
import org.jetbrains.kotlin.ir.expressions.*


class TransformQueryMap(override val ctx: TransformerOrigin, val matcher: ExtractorsDomain.QueryFunction, val replacementMethod: String): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    matcher.matchesMethod(expression)

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall, superTransformer: io.exoquery.plugin.CaptureTransformer): IrExpression {

    // TODO use the parserCtx from the ParserContext input
    val decls = ScopeSymbols(CollectDecls.from(expression)) + ctx.parentScopeSymbols
    val parserCtx = ParserContext(decls, ctx.currentFile)

    fun <R> withContext(value: context(ParserContext, CompileLogger) () -> R) =
      value(parserCtx, logger)

    val (caller, funExpression, params, blockBody) =
      on(expression).match(
        // e.g. QueryMap[Is()]
        case(matcher[Is()]).then { queryCallData -> queryCallData }
      ) ?: parseFail("Illegal block on function:\n${Messages.PrintingMessage(expression)}")
      // TODO Needs to convey SourceLocation coordinates, look into the 'warn/error' thing to see how to do that
      //      also, should generally just return the expression instead of throwing exceptions in the compiler
      //      perhaps use some kind of FP either-like structure here???

    val lambdaArg = params.first()

    val paramIdentXR = run {
      val name = lambdaArg.name.asString()
      val tpe = TypeParser.parse(lambdaArg.type)
      XR.Ident(name, tpe)
    }

    val (bodyXR, bindsAccum) = Parser.parseFunctionBlockBody(blockBody)

    val expr = Lambda1Expression(XR.Function1(paramIdentXR, bodyXR))
    val liftedExpr = makeLifter().liftExpression(expr)

//    val mapExprFunction = caller.type.findMethodOrFail(replacementMethod)

    // need to recursively transformed parent e.g. InvokeFunction(TableQuery(stuff), "map") needs to returse
    // into the TableQuery part. Also make sure to call caller.transform(superTransformer) instead of superTransformer.visitExpression
    // because superTransformer.visitExpression won't recurse into child-types of Expression e.g. IrCall (Ugh!)

    // TODO Propagate ScopeSymbols, pass it in from the input TransformScope, add symbols
    //      that we've collected here, then make the recursive call
    val transformedCaller = caller.transform(superTransformer, decls)

    val bindsList = bindsAccum.makeDynamicBindsIr()

    val expressionCall =
      // for:  query.map(p -> p.name)
      // it would be:  (query).callMethodWithType("map", <String>. bindsList())(XR.Function1(Id(p), Prop(p, name))
      transformedCaller.callMethodWithType(replacementMethod, expression.type)(liftedExpr, bindsList)

    return expressionCall
  }


}