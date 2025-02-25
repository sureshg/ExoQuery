package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.exoquery.CapturedBlock
import io.exoquery.Ord
import io.exoquery.xr.SelectClause
import io.exoquery.SqlQuery
import io.exoquery.annotation.DslFunctionCall
import io.exoquery.annotation.DslNestingIgnore
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.TransformerScope
import io.exoquery.parseError
import io.exoquery.plugin.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.transform.LocateableContext
import io.exoquery.plugin.trees.ExtractorsDomain.Call.`x to y`
import io.exoquery.xr.*
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName


data class LocationContext(val internalVars: TransformerScope, override val currentFile: IrFile): LocateableContext {
  fun newParserCtx() = ParserContext(this)
  fun withCapturedFunctionParameters(capturedFunctionParameters: List<IrValueParameter>) =
    LocationContext(internalVars.withCapturedFunctionParameters(capturedFunctionParameters), currentFile)
  fun withSymbols(symbols: List<IrSymbol>) =
    LocationContext(internalVars.withSymbols(symbols), currentFile)
}

data class ParserContext(val location: LocationContext, val binds: DynamicsAccum = DynamicsAccum.newEmpty()): LocateableContext {
  val internalVars get() = location.internalVars
  val capturedFunctionSymbols get() = internalVars.capturedFunctionParameters
  override val currentFile get() = location.currentFile
}

inline fun <reified R> Is.Companion.of(vararg possibilities: R): Is<R> = Is.PredicateAs(io.decomat.Typed<R>(), { possibilities.contains(it) })

object Parser {
  context(LocationContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): Pair<XR, DynamicsAccum> =
    with (newParserCtx()) {
      ParseExpression.parseFunctionBlockBody(blockBody) to binds
    }

  context(LocationContext, CompileLogger) fun parseQuery(expr: IrExpression): Pair<XR.Query, DynamicsAccum> =
    with (newParserCtx()) {
      ParseQuery.parse(expr) to binds
    }

  context(LocationContext, CompileLogger) fun parseSelectClauseLambda(expr: IrExpression): Pair<SelectClause, DynamicsAccum> =
    with (newParserCtx()) {
      ParseSelectClause.parseSelectLambda(expr) to binds
    }

  context(LocationContext, CompileLogger) fun parseExpression(expr: IrExpression): Pair<XR.Expression, DynamicsAccum> =
    with (newParserCtx()) {
      ParseExpression.parse(expr) to binds
    }

  context(LocationContext, CompileLogger) fun parseValueParamter(expr: IrValueParameter): XR.Ident =
    with (newParserCtx()) { expr.makeIdent() }

  context(ParserContext, CompileLogger)
  internal fun parseArg(arg: IrExpression) =
    when {
      arg.type.isClass<SqlQuery<*>>() -> ParseQuery.parse(arg)
      else -> ParseExpression.parse(arg)
    }
}

context(ParserContext, CompileLogger)
fun IrValueParameter.makeIdent() =
  XR.Ident(this.name.asString(), TypeParser.of(this), this.locationXR())

fun IrFunctionExpression.firstParam() =
  this.function.simpleValueParams[0]

fun IrCall.extensionOrDispatch() =
  this.extensionReceiver ?: this.dispatchReceiver

// Parser for GlobalCall and MethodCall
object CallParser {
  context(ParserContext, CompileLogger)
  fun parse(expr: IrCall): XR.U.QueryOrExpression {

    fun IrSimpleFunctionSymbol.toFqNameXR() = this.owner.kotlinFqName.toXR()
    val reciever = expr.extensionReceiver ?: expr.dispatchReceiver

    return when {
      // for things like string.sql.left(...) ignore the "sql" part
      reciever != null && reciever is IrCall && reciever.ownerHasAnnotation<DslNestingIgnore>() && reciever.extensionOrDispatch() is IrCall -> {
        parseCall(reciever.extensionOrDispatch(), expr)
      }
      else -> parseCall(reciever, expr)
    }
  }

  context(ParserContext, CompileLogger)
  private fun parseCall(reciever: IrExpression?, expr: IrCall): XR.U.QueryOrExpression =
    when {
      reciever == null || reciever.type.isClass<CapturedBlock>() ->
        XR.GlobalCall(
          name = expr.symbol.owner.kotlinFqName.toXR(),
          args = expr.simpleValueArgs.map { arg -> arg?.let { Parser.parseArg(it) } ?: XR.Const.Null() },
          callType = expr.extractCallType(),
          type = TypeParser.of(expr),
          loc = expr.loc
        )
      else ->
        XR.MethodCall(
          head = Parser.parseArg(reciever!!),
          name = expr.symbol.safeName,
          args = expr.simpleValueArgs.map { arg -> arg?.let { Parser.parseArg(it) } ?: XR.Const.Null() },
          originalHostType = expr.type.classId()?.toXR() ?: XR.ClassId.Empty,
          type = TypeParser.of(expr),
          loc = expr.loc,
          callType = expr.extractCallType()
        )
    }

  context(ParserContext, CompileLogger)
  private fun IrCall.extractCallType(): XR.CallType {
    val arg =
      (this.symbol.owner.getAnnotationArgs<DslFunctionCall>().firstOrNull() ?: parseError("Could not find DslFunctionCall annotation", this))
        as? IrClassReference ?: parseError("DslFunctionCall annotation must have a single argument that is a class-reference (e.g. PureFunction::class)", this)
    val argXR =
      arg.classType.classFqName?.shortName()?.asString()?.let { XR.CallType.fromClassString(it) }
        ?: parseError("Could not parse CallType from: ${arg.dumpKotlinLike()}", arg)
    return argXR
  }



}
