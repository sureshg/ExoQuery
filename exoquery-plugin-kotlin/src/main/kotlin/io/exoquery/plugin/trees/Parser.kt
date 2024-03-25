package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.BID
import io.exoquery.Query
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.DomainErrors
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.ScopeSymbols
import io.exoquery.parseError
import io.exoquery.plugin.*
import io.exoquery.plugin.transform.VisitTransformExpressions
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import java.util.UUID

data class ParserContext(val internalVars: ScopeSymbols, val currentFile: IrFile)

object Parser {
  context(ParserContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): Pair<XR, DynamicBindsAccum> =
    ParserCollector().let { par -> Pair(par.parseFunctionBlockBody(blockBody), par.binds) }

  context(ParserContext, CompileLogger) fun parseExpression(expr: IrExpression): Pair<XR, DynamicBindsAccum> =
    ParserCollector().let { par -> Pair(par.parse(expr), par.binds) }
}

/**
 * Parses the tree and collets dynamic binds as it goes. The parser should be exposed
 * as stateless to client functions so everything should go through the `Parser` object instead of this.
 */
private class ParserCollector {
  context(ParserContext) private val IrElement.loc get() = this.locationXR()

  val binds = DynamicBindsAccum.empty()

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
        val irType = TypeParser.parse(type)
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

//  fun ownerChain(symbol: IrSymbol) =
//    "------ Ownership Chain: ${symbol.safeName} -> ${symbol.owner.dumpKotlinLike()} -> ${
//      when(val owner = symbol.owner) {
//        is IrValueParameter -> "Parent: " + owner.parent.dumpSimple()
//        else -> "Done"
//      }
//    }"



  context(ParserContext, CompileLogger) fun parse(expr: IrExpression): XR =
    // adding the type annotation <Ast> seems to improve the type inference performance

    // TODO was in the middle of working on pattern-matching for Unary functions
    on(expr).match<XR>(

      // This could a runtime binding e.g. a variable representing a query etc...
      // can we assume it will be recurisvely transformed and just take the XR from it?
      // or do we need to transform it here
      case(Ir.Expression[Is()]).thenIf { it.isClass<Query<*>>() }.then { expr ->
        // Assuming that recursive transforms have already converted queries inside here
        val bindId = BID.new()

        // Add the query expression to the binds list
        binds.add(bindId, RuntimeBindValueExpr.RuntimeQueryExpr(expr))

        XR.RuntimeQueryBind(bindId, TypeParser.parse(expr.type), expr.loc)
      },

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

      // TODO also need unary operator


      // TODO exclude anything here that's not an SqlVariable
      case (ExtractorsDomain.Call.InvokeSqlVariable[Is()]).thenThis { symName ->
        // Add the bind to the parser context to be returned when parsing is done
        val bindId = BID.new()
        //warn("=================== Making new Bind: ${bindId} ===================")
        binds.add(
          bindId, /*the SqlVariable instance*/
          this.dispatchReceiver?.let { RuntimeBindValueExpr.SqlVariableIdentExpr(it) } ?: DomainErrors.NoDispatchRecieverFoundForSqlVarCall(this)
        )
        //warn(binds.show().toString())
        XR.IdentOrigin(bindId, symName, TypeParser.parse(this.type), expr.locationXR())
      },

      // Other situations where you might have an identifier which is not an SqlVar e.g. with variable bindings in a Block (inside an expression)
      case(Ir.GetValue[Is()]).thenThis { sym ->
        // Every single instance of this should should be a getSqlVar
        //error("----------- Checking: ${sym.safeName} against internalVars: ${internalVars.symbols.map { it.safeName }}")

        //  let-alises e.g: tmp0_safe_receiver since can't error on them
        if (!internalVars.contains(sym) && !(sym.owner.let { it is IrVariable && it.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE }) ) {
          val loc = this.location()
          // TODO Need much longer and better error message (need to say what the clause is)
          error("The symbol `${sym.safeName}` is external. Cannot find it in the symbols-list belonging to the clause ${internalVars.symbols.map { it.safeName }}", loc)
        }

        XR.Ident(sym.safeName, TypeParser.parse(this.type), this.locationXR()) // this.symbol.owner.type

        // TODO Need to enhance parseFail to return the failed symbol so later when the
        //      compile-time message is produced we can get the row/column of the bad symbol
        //parseFail("The symbol `${sym.safeName}` is external. Cannot find it in the symbols-list belonging to the clause ${internalVars.symbols.map { it.safeName }}")

      },
      case(Ir.Const[Is()]).thenThis {
        parseConst(this)
      },
      case(Ir.Call.Property[Is(), Is()]).then { expr, name ->
        XR.Property(parseExpr(expr), name, XR.Visibility.Visible, expr.loc)
      },
      case(Ir.Call.FunctionUntethered1[Is()]).thenIfThis { list ->
        this.symbol.safeName == "getSqlVar"
      }.thenThis { arg ->
        val argValue =
          when (arg) {
            is IrConst<*> -> arg.value.toString()
            else -> parseError("Illegal argument in the `getSqlVar` function:\n${this.dumpKotlinLike()}")
          }

        XR.Ident(argValue, TypeParser.parse(this.type), this.locationXR())
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
        val elseBranchOrLast = elseBranch ?: casesAst.lastOrNull() ?: parseError("Empty when expression not allowed:\n${this.dumpKotlinLike()}")
        XR.When(casesAst, elseBranchOrLast.then, expr.loc)
      }
    ) ?: parseError(
      """|======= Could not parse expression from: =======
         |${expr.dumpKotlinLike()}
         |--------- With the Tree ---------
         |${expr.dumpSimple()}
         |
      """.trimMargin())

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
