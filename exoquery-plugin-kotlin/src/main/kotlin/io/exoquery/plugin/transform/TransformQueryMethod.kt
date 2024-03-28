package io.exoquery.plugin.transform

import io.decomat.*
import io.exoquery.parseError
import io.exoquery.plugin.locationXR
import io.exoquery.xr.XR
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.dumpSimple
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import io.exoquery.plugin.trees.CallData.MultiArgMember.ArgType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike


class TransformQueryMethod(override val ctx: BuilderContext, val matcher: ExtractorsDomain.QueryDslFunction, val superTransformer: VisitTransformExpressions): Transformer() {
  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    matcher.matchesMethod(expression)

  context(ParserContext, BuilderContext, CompileLogger) private fun lambdaArgToXR(lambdaArg: IrValueParameter, location: XR.Location): IrExpression {
    val lifter = makeLifter()
    val paramIdentXR = run {
      val name = lambdaArg.name.asString()
      val tpe = TypeParser.of(lambdaArg)
      XR.Ident(name, tpe, location)
    }
    val paramIdentExpr = lifter.liftIdent(paramIdentXR)
    return paramIdentExpr
  }

  context(ParserContext, BuilderContext, CompileLogger) private fun lambdaArgsToListOfXR(lambdaArgs: List<IrValueParameter>, location: XR.Location): IrExpression {
    val lifter = makeLifter()
    val lambdaArgsExprs = lambdaArgs.map { arg -> lambdaArgToXR(arg, location) }
    return lifter.liftList(lambdaArgsExprs) { it }
  }

  context(ParserContext, BuilderContext, CompileLogger)
  protected fun processBlockBody(blockBody: IrBlockBody): Pair<IrExpression, IrExpression> {
    // If there are any maps/filters/flatMaps etc... in the body need to transform them first
    val lifter = makeLifter()
    val transformedBlockBody = blockBody.transform(superTransformer, internalVars) as IrBlockBody
    val (bodyXR, bindsAccum) = Parser.parseFunctionBlockBody(transformedBlockBody)
    val bindsListExpr = bindsAccum.makeDynamicBindsIr()
    return lifter.liftXR(bodyXR) to bindsListExpr
  }


  // parent symbols are collected in the parent context
  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression {
    val callData =
      matcher.extract(expression) ?: parseError("Illegal block on function:\n${Messages.PrintingMessage(expression)}")
      // TODO Needs to convey SourceLocation coordinates, look into the 'warn/error' thing to see how to do that
      //      also, should generally just return the expression instead of throwing exceptions in the compiler
      //      perhaps use some kind of FP either-like structure here???

    val lifter = makeLifter()
    val locExpr = lifter.liftLocation(expression.locationXR())

    return when (val callDataDetails = callData.first) {
        is CallData.LambdaMember -> {
          val (caller, _, params, blockBody) = callDataDetails
          // List of the arguments passed to the lambda as identifiers
          val paramIdentExpr = lambdaArgsToListOfXR(params, expression.locationXR())
          val transformedCaller = caller.transform(superTransformer, internalVars)
          val (bodyExpr, bindsList) = processBlockBody(blockBody)
          //error("============ Caller Type is: ${callData.second.callerType}")

          // for:  query.map(p -> p.name)
          // it would be:  (query).callMethodWithType("map", <String>. bindsList())(XR.Function1(Id(p), Prop(p, name))
          transformedCaller.callMethodWithType(callData.second, expression.type)(paramIdentExpr, bodyExpr, bindsList, locExpr)
        }
        is CallData.MultiArgMember -> {
          val (caller, argValues) = callDataDetails
          val transformedCaller = caller.transform(superTransformer, internalVars)
          data class Fold(val args: List<IrExpression>, val binds: DynamicBindsAccum)
          val (args, binds) =
            argValues.fold(Fold(listOf(), DynamicBindsAccum())) { fold, (argType, argExpr) ->
              when (argType) {
                is ArgType.Passthrough -> Fold(fold.args + argExpr, fold.binds)
                is ArgType.ParsedXR -> {
                  val (body, binds) = Parser.parseExpression(argExpr)
                  val liftedXR = lifter.liftXR(body)
                  Fold(fold.args + liftedXR, fold.binds + binds)
                }
              }
            }
          // each argument (passthrough or not) followed by the binds and the location e.g: for some hypothetical call sortedByOrd:
          // query.sortedByOrd(@ParseXR expr = person -> person.name, direction = Asc) =>
          //  query.sortedByOrdExpr(XR.Expression, Asc, binds, location)
          // (this call is not actually used but serves as an example)
          val allArgs = listOf(*args.toTypedArray()) + listOf(binds.makeDynamicBindsIr(), locExpr)
          transformedCaller.callMethodWithType(callData.second, expression.type)(*allArgs.toTypedArray())
        }
      }
  }
}