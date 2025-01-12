package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.caseEarly
import io.decomat.match
import io.decomat.on
import io.exoquery.BID
import io.exoquery.SqlExpression
import io.exoquery.SqlQuery
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.ScopeSymbols
import io.exoquery.parseError
import io.exoquery.plugin.*
import io.exoquery.xr.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
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

  context(ParserContext, CompileLogger) fun parseBlockStatement(expr: IrStatement): XR.Variable =
    on(expr).match(
      case(Ir.Variable[Is(), Is()]).thenThis { name, rhs ->
        val irType = TypeParser.of(this)
        XR.Variable(XR.Ident(name, irType, rhs.locationXR()), parseExpr(rhs), expr.loc)
      }
    ) ?: parseError("Could not parse Ir Variable statement from:\n${expr.dumpSimple()}")

  context(ParserContext, CompileLogger) fun parseBranch(expr: IrBranch): XR.Branch =
    on(expr).match(
      case(Ir.Branch[Is(), Is()]).then { cond, then ->
        XR.Branch(parseExpr(cond), parseExpr(then), expr.loc)
      }
    ) ?: parseError("Could not parse Branch from: ${expr.dumpSimple()}")

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


      case(ExtractorsDomain.CaseClassConstructorCall[Is()]).then { data ->
        XR.Product(data.className, data.fields.map { (name, valueOpt) -> name to (valueOpt?.let { parseExpr(it) } ?: XR.Const.Null(expr.loc)) }, expr.loc)
      },

      case(Ir.Call.FunctionMem1.WithCaller[Is(), Is("param"), Is()]).thenThis { _, paramValue ->
        val bid = BID.new()
        binds.addParam(bid, paramValue)
        XR.TagForParam(bid, TypeParser.of(paramValue), paramValue.loc)
      },

      // TODO need to test this case i.e. where this is propagated

      // TODO ExtractorsDomain should probably have something like SqlQuery_use to represent `SqlQuery.use`
      case(Ir.Call.FunctionMem0[Is(), Is("use")]).thenIf { useExpr, _ -> useExpr.type.isClass<SqlQuery<*>>() }.then { sqlQueryIr, _ ->
        sqlQueryIr.match(
          case(SqlQueryExpr.Uprootable[Is()]).then { uprootable ->
            // Add all binds from the found SqlQuery instance, this will be truned into something like `currLifts + SqlQuery.lifts` late
            binds.addAllParams(sqlQueryIr)
            // Then unpack and return the XR
            val uprooted = uprootable.xr // TODO catch errors here?
            XR.ValueOf(uprooted, sqlQueryIr.loc)  // TODO more detailed error message
          },
        ) ?: run {
          val bid = BID.new()
          binds.addRuntime(bid, sqlQueryIr)
          XR.ValueOf(XR.TagForSqlQuery(bid, TypeParser.of(sqlQueryIr), sqlQueryIr.loc))
        }
      },
      // Now the same for SqlExpression
      case(Ir.Call.FunctionMem0[Is(), Is("use")]).thenIf { useExpr, _ -> useExpr.type.isClass<SqlExpression<*>>() }.then { sqlExprIr, _ ->
        sqlExprIr.match(
          case(SqlExpressionExpr.Uprootable[Is()]).then { uprootable ->
            // Add all binds from the found SqlExpression instance, this will be truned into something like `currLifts + SqlExpression.lifts` late
            binds.addAllParams(sqlExprIr)
            // Then unpack and return the XR
            uprootable.xr
          }
          // TODO add dynamic case
        ) ?: run {
          val bid = BID.new()
          binds.addRuntime(bid, sqlExprIr)
          XR.TagForSqlExpression(bid, TypeParser.of(sqlExprIr), sqlExprIr.loc)
        }
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

      case(ExtractorsDomain.Call.`x to y`[Is(), Is()]).thenThis { x, y ->
        XR.Product.Tuple(parseExpr(x), parseExpr(y), expr.loc)
      },

      // Other situations where you might have an identifier which is not an SqlVar e.g. with variable bindings in a Block (inside an expression)
      case(Ir.GetValue[Is()]).thenThis { sym ->

        // TODO re-enable scope vars and pass them into here.

        //if (!internalVars.contains(sym) && !(sym.owner.let { it is IrVariable && it.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE }) ) {
        //  val loc = this.location()
        //  // TODO Need much longer and better error message (need to say what the clause is)
        //  error("The symbol `${sym.safeName}` is external. Cannot find it in the symbols-list belonging to the clause ${internalVars.symbols.map { it.safeName }}", loc)
        //}

        XR.Ident(sym.safeName, TypeParser.of(this), this.locationXR()) // this.symbol.owner.type
      },
      case(Ir.Const[Is()]).thenThis {
        parseConst(this)
      },
      case(Ir.Call.Property[Is(), Is()]).then { expr, name ->
        XR.Property(parseExpr(expr), name, XR.Visibility.Visible, expr.loc)
      },

      // case(Ir.Call.Function[Is()]).thenIf { (list) -> list.size == 2 }.thenThis { list ->
      //   val a = list.get(0)
      //   val b = list.get(1)
      //   // TODO need to check that's its actually a binary operator!!!
      //   XR.BinaryOp(parse(a), parseSymbol(this.symbol), parse(b))
      // }
      // ,
      case(Ir.Block[Is(), Is()]).then { stmts, ret ->
        XR.Block(stmts.map { parseBlockStatement(it) }, parseExpr(ret), expr.loc)
      },
      case(Ir.When[Is()]).thenThis { cases ->
        val elseBranch = cases.find { it is IrElseBranch }?.let { parseBranch(it) }
        val casesAst = cases.filterNot { it is IrElseBranch }.map { parseBranch(it) }
        val allReturnsAreBoolean = cases.all { it.result.type.isClass<Boolean>() }

        // Kotlin converts (A && B) to `if(A) B else false`. This undoes that
        if (
            allReturnsAreBoolean &&
            elseBranch != null && casesAst.size == 1
              && casesAst.first().then.type is XRType.Boolean
              // Implicitly the else-clause in this case cannot have additional conditions
              && elseBranch.cond == XR.Const.Boolean(true) && elseBranch.then == XR.Const.Boolean(false)
          ) {
          val firstClause = casesAst.first()
          firstClause.cond `+and+` firstClause.then
        }
        // Kotlin converts (A || B) to `if(A) true else B`. This undoes that
        else if (
          allReturnsAreBoolean &&
          elseBranch != null && casesAst.size == 1
          && casesAst.first().then == XR.Const.Boolean(true)
          // Implicitly the else-clause in this case cannot have additional conditions
          && elseBranch.cond == XR.Const.Boolean(true)
        ) {
          val firstClause = casesAst.first()
          firstClause.cond `+or+` elseBranch.then
        }
        else {
          val elseBranchOrLast = elseBranch ?: casesAst.lastOrNull() ?: parseError("Empty when expression not allowed:\n${this.dumpKotlinLike()}")
          XR.When(casesAst, elseBranchOrLast.then, expr.loc)
        }
      },



      // Need to allow possibility of nulls here because even simple things like String.split can have nulls (i.e. for the 2nd/3rd args)
//      case(Ir.Call.FunctionMemAllowNulls[Is(), Is()]).thenIfThis { caller, args ->
//        methodWhitelist.containsMethod(symbol.safeName)
//      }.thenThis { caller, args ->
//        val methodCallName = XR.MethodCallName(symbol.fqNameXR(), caller.reciver.type.fqNameXR())
//        if (!methodWhitelist.contains(methodCallName))
//          throwParseErrorMsg(expr, "The expression was not in the whitelist.", "The expression was: \n${methodCallName}")
//
//        fun parseArg(expr: IrExpression): List<XR.Expression> =
//          when (expr) {
//            is IrVararg -> {
//              expr.elements.toList().map { elem ->
//                when (elem) {
//                  is IrExpression -> parseExpr(elem)
//                  else -> throwParseErrorMsg(elem, "Invalid Variadic Element")
//                }
//              }
//            }
//            else -> listOf(parseExpr(expr))
//          }
//
//        XR.MethodCall(
//          parseExpr(caller.reciver), methodCallName, args.flatMap { arg -> arg?.let { parseArg(it) } ?: listOf(XR.Const.Null(locationXR())) },
//          TypeParser.of(symbol.owner), locationXR()
//        )
//      },
    ) ?: throwParseErrorMsg(expr)

//  fun IrSimpleFunctionSymbol.fqNameXR() =
//    XR.FqName(this.owner.parent.kotlinFqName.toString(), this.safeName)
//
//  fun IrType.fqNameXR() =
//    this.classFqName?.let { name ->
//      XR.FqName(name.parent().toString(), name.shortName().asString())
//    } ?: parseError("Could not get the classFqName for the type: ${this.dumpKotlinLike()}")


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

  context (ParserContext, CompileLogger) fun parseConst(irConst: IrConst): XR =
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
