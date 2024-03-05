package io.exoquery.plugin.transform

import io.decomat.*
import io.exoquery.Lambda1Expression
import io.exoquery.parseError
import io.exoquery.xr.XR
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.CollectDecls
import io.exoquery.plugin.safeName
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike


class TransformQueryMap(override val ctx: BuilderContext, val matcher: ExtractorsDomain.QueryFunction, val replacementMethod: String): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    matcher.matchesMethod(expression)

  // parent symbols are collected in the parent context
  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall, superTransformer: VisitTransformExpressions): IrExpression {

    // TODO use the parserCtx from the ParserContext input
    // error("------ Collecting Decls from: ${expression.dumpKotlinLike()} = ${CollectDecls.from(expression).map { it.safeName }}")
    // error("------ Decls from parent scope: ${ctx.parentScopeSymbols.symbols.map { it.safeName }}")

    val (caller, funExpression, params, blockBody) =
      on(expression).match(
        // e.g. QueryMap[Is()]
        case(matcher[Is()]).then { queryCallData -> queryCallData }
      ) ?: parseError("Illegal block on function:\n${Messages.PrintingMessage(expression)}")
      // TODO Needs to convey SourceLocation coordinates, look into the 'warn/error' thing to see how to do that
      //      also, should generally just return the expression instead of throwing exceptions in the compiler
      //      perhaps use some kind of FP either-like structure here???

    val lambdaArg = params.first()

    val paramIdentXR = run {
      val name = lambdaArg.name.asString()
      val tpe = TypeParser.parse(lambdaArg.type)
      XR.Ident(name, tpe)
    }

    // If there are any maps/filters/flatMaps etc... in the body need to transform them first
    val transformedBlockBody = blockBody.transform(superTransformer, internalVars) as IrBlockBody
    val (bodyXR, bindsAccum) = Parser.parseFunctionBlockBody(transformedBlockBody)

    val paramIdentExpr = makeLifter().liftXR(paramIdentXR)
    val bodyExpr = makeLifter().liftXR(bodyXR)

//    val mapExprFunction = caller.type.findMethodOrFail(replacementMethod)

    // need to recursively transformed parent e.g. InvokeFunction(TableQuery(stuff), "map") needs to returse
    // into the TableQuery part. Also make sure to call caller.transform(superTransformer) instead of superTransformer.visitExpression
    // because superTransformer.visitExpression won't recurse into child-types of Expression e.g. IrCall (Ugh!)

    // TODO Propagate ScopeSymbols, pass it in from the input TransformScope, add symbols
    //      that we've collected here, then make the recursive call
    val transformedCaller = caller.transform(superTransformer, internalVars)

    val bindsList = bindsAccum.makeDynamicBindsIr()

    val expressionCall =
      // for:  query.map(p -> p.name)
      // it would be:  (query).callMethodWithType("map", <String>. bindsList())(XR.Function1(Id(p), Prop(p, name))
      transformedCaller.callMethodWithType(replacementMethod, expression.type)(paramIdentExpr, bodyExpr, bindsList)

    return expressionCall
  }


}