package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.decomat.on
import io.exoquery.BID
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.ScopeSymbols
import io.exoquery.parseError
import io.exoquery.plugin.*
import io.exoquery.xr.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

data class ParserContext(val internalVars: ScopeSymbols, val currentFile: IrFile)



object Parser {
  context(ParserContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): Pair<XR, DynamicsAccum> =
    ParserCollector().let { par -> Pair(par.parseFunctionBlockBody(blockBody), par.binds) }

  // TODO rename to invoke? or just parse
  context(ParserContext, CompileLogger) fun parseExpression(expr: IrExpression): Pair<XR, DynamicsAccum> =
    ParserCollector().let { par -> Pair(par.parse(expr), par.binds) }
}

/**
 * Parses the tree and collets dynamic binds as it goes. The parser should be exposed
 * as stateless to client functions so everything should go through the `Parser` object instead of this.
 */
private class ParserCollector {
  context(ParserContext) private val IrElement.loc get() = this.locationXR()

  val binds = DynamicsAccum.newEmpty()

  // TODO need to parse interpolations

  context(ParserContext, CompileLogger) inline fun <reified T> parseAs(expr: IrExpression): T {
    val parsedExpr = parse(expr)
    return if (parsedExpr is T) parsedExpr
    else parseError(
      """|Could not parse the type expected type ${T::class.qualifiedName} (actual was ${parsedExpr::class.qualifiedName}) 
         |${expr.dumpKotlinLike()}
      """.trimMargin()
    )
  }

  context(ParserContext, CompileLogger) fun parseExpr(expr: IrExpression): XR.Expression =
    parseAs<XR.Expression>(expr)

  context(ParserContext, CompileLogger) fun parseXR(expr: IrExpression): XR =
    parseAs<XR>(expr)


  context(ParserContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): XR =
    on(blockBody).match<XR>(
      // TODO use Ir.BlockBody.ReturnOnly
      case(Ir.BlockBody[List1[Ir.Return[Is()]]])
        .then { (irReturn) ->
          val returnExpression = irReturn.value
          parse(returnExpression)
        }
    ) ?: parseError("Could not parse IrBlockBody:\n${blockBody.dumpKotlinLike()}")

  context(ParserContext, CompileLogger) fun parse(expr: IrExpression): XR =
    // adding the type annotation <Ast> seems to improve the type inference performance

    // TODO was in the middle of working on pattern-matching for Unary functions


    on(expr).match<XR>(
      // ExtractorsDomain.CaseClassConstructorCall1[Is(), Is()]
      //
      //case(Ir.Call.FunctionMem0[ExtractorsDomain.CaseClassConstructorCall1[Is(), Is()], Is("use")]).then { (sqlExprUprootable), _ ->

      // Direct capture-in-capture splice e.g:  `capture { capture { 1 }.use }`
      case(Ir.Call.FunctionMem0[SqlExpressionExpr.Uprootable[Is()], Is("use")]).then { (sqlExprUprootable), _ ->
        // E.g. when you've got something like `capture { 2 + capture { 1 + param(123) }.use }`
        // It turs into something like `capture { 2 + SqlExpression(unpack(...), params=Param(123)) }`
        // So we need to all all of the params coming from the inner SqlExpression instance
        binds.addAllParams(compLeft)
        sqlExprUprootable.xr
      },

      case(Ir.Call.FunctionMem1.WithCaller[Is(), Is("param"), Is()]).thenThis { _, paramValue ->
        val bid = BID.new()
        binds.addParam(bid, paramValue)
        XR.Const.String("UUID:${bid.value}")
      },

      case(Ir.Call.FunctionMem0[Ir.Call[Is()], Is("use")]).thenIf { _, _ -> compLeft.type.isClass<io.exoquery.SqlExpression<*>>() }.thenThis { (varCall), _ ->
        //sym.owner.match(
        //  case(Ir.Variable[Is(), SqlExpressionExpr.Uprootable[Is()]]).thenThis { varName, (uprootable) ->
        //    error("----------------- Got to Owner of ------------\n${uprootable.xr.show()}")
        //    uprootable.xr
        //  }
        //)

        error("""
          |------------- Get of SqlExpression -------------
          |${expr.dumpKotlinLike()}
          |------- with IR --------
          |${expr.dumpSimple()}
          |-----------------------------------------------
          |with Owner :
          |${varCall.symbol.owner.dumpKotlinLike()}
          |------- with Owner IR: 
          |${varCall.symbol.owner.dumpSimple()}
          """.trimMargin())
        XR.Const.String("foo")
      },

      /*
      case(Ir.Call.FunctionMem0[Is(), Is("use")]).thenIf { calledFrom, _ -> calledFrom is IrCall && calledFrom.type.isClass<io.exoquery.SqlExpression<*>>() }.thenThis { calledFrom, _ ->
        //sym.owner.match(
        //  case(Ir.Variable[Is(), SqlExpressionExpr.Uprootable[Is()]]).thenThis { varName, (uprootable) ->
        //    error("----------------- Got to Owner of ------------\n${uprootable.xr.show()}")
        //    uprootable.xr
        //  }
        //)

        error("""
          |------------- Get of SqlExpression -------------
          |${calledFrom.dumpKotlinLike()}
          |------- with IR: --------
          |${calledFrom.dumpSimple()}
          |-----------------------------------------------
          |with Owner :
          |${(calledFrom as IrCall).symbol.owner.dumpKotlinLike()}
          |------- with Owner IR: --------
          |${(calledFrom as IrCall).symbol.owner.dumpSimple()}
          """.trimMargin())
        XR.Const.String("foo")
      },
       */

      //case(Ir.Call.FunctionMem0[Is(), Is("use")]).then { v, _ ->
      //  error("------------ Calling Use Function ---------\n${v.dumpKotlinLike()}")
      //  XR.Const.String("foo")
      //},

      // Binary Operators
      case(ExtractorsDomain.Call.`x op y`[Is()]).thenThis { opCall ->
        val (x, op, y) = opCall
        XR.BinaryOp(parseAs<XR.Expression>(x), op, parseAs<XR.Expression>(y), expr.loc)
      },
      // Unary Operators
      case(ExtractorsDomain.Call.`(op)x`[Is()]).thenThis { opCall ->
        val (x, op) = opCall
        XR.UnaryOp(op, parseAs<XR.Expression>(x), expr.loc)
      },


      case(Ir.GetValue[Is()]).thenThis { sym ->
        XR.Ident(sym.safeName, TypeParser.of(this), this.locationXR()) // this.symbol.owner.type
      },
      case(Ir.Const[Is()]).thenThis {
        parseConst(this)
      }
    ) ?: throwParseErrorMsg(expr)

  context (CompileLogger, ParserContext) fun throwParseErrorMsg(expr: IrElement, heading: String = "Could not parse expression from", additionalMsg: String = ""): Nothing {
    parseError(
      """|${expr.location().show()}}
         |======= ${heading}: =======
         |${expr.dumpKotlinLike()}
         |--------- With the Tree ---------
         |${expr.dumpSimple()}
      """.trimMargin() + if (additionalMsg != "") "\n" + "--------- Additional Data ---------\n" + additionalMsg else ""
    )
  }

  context (ParserContext, CompileLogger) fun parseConst(irConst: IrConst<*>): XR =
    if (irConst.value == null) XR.Const.Null(irConst.loc)
    else when (irConst.kind) {
      IrConstKind.Null -> XR.Const.Null(irConst.loc)
      IrConstKind.Boolean -> XR.Const.Boolean(irConst.value as kotlin.Boolean, irConst.loc)
      IrConstKind.Char -> XR.Const.Char(irConst.value as kotlin.Char, irConst.loc)
      IrConstKind.Byte -> XR.Const.Byte(irConst.value as kotlin.Int, irConst.loc)
      IrConstKind.Short -> XR.Const.Short(irConst.value as kotlin.Short, irConst.loc)
      IrConstKind.Int -> XR.Const.Int(irConst.value as kotlin.Int, irConst.loc)
      IrConstKind.Long -> XR.Const.Long(irConst.value as kotlin.Long, irConst.loc)
      IrConstKind.String -> XR.Const.String(irConst.value as kotlin.String, irConst.loc)
      IrConstKind.Float -> XR.Const.Float(irConst.value as kotlin.Float, irConst.loc)
      IrConstKind.Double -> XR.Const.Double(irConst.value as kotlin.Double, irConst.loc)
      else -> parseError("Unknown IrConstKind: ${irConst.kind}")
    }


}
