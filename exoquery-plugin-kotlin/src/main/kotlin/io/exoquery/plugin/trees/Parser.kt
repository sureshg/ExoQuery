package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.plugin.location
import io.exoquery.xr.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.safeName
import io.exoquery.plugin.transform.ScopeSymbols
import io.exoquery.plugin.transform.parseFail
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike


data class ParserContext(val internalVars: ScopeSymbols, val currentFile: IrFile)

object Parser {

  context(ParserContext, CompileLogger) fun parseBlockStatement(expr: IrStatement): XR.Variable =
    on(expr).match(
      case(Ir.Variable[Is(), Is()]).then { name, rhs ->
        XR.Variable(name, parse(rhs))
      }
    ) ?: parseFail("Could not parse Ir Variable statement from:\n${expr.dumpSimple()}")

  context(ParserContext, CompileLogger) fun parseBranch(expr: IrBranch): XR.Branch =
    on(expr).match(
      case(Ir.Branch[Is(), Is()]).then { cond, then ->
        XR.Branch(parse(cond), parse(then))
      }
    ) ?: parseFail("Could not parse Branch from: ${expr.dumpSimple()}")

  context(ParserContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): XR =
    on(blockBody).match<XR>(
      // TODO use Ir.BlockBody.ReturnOnly
      case(Ir.BlockBody[List1[Ir.Return[Is()]]])
        .then { (irReturn) ->
          val returnExpression = irReturn.value
          parse(returnExpression)
        }
    ) ?: parseFail("Could not parse IrBlockBody:\n${blockBody.dumpKotlinLike()}")

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

      // Binary Operators
      case(ExtractorsDomain.Call.`x op y`[Is()]).thenThis { opCall ->
        val (x, op, y) = opCall
        XR.BinaryOp(parse(x), op, parse(y))
      },
      // Unary Operators
      case(ExtractorsDomain.Call.`(op)x`[Is()]).thenThis { opCall ->
        val (x, op) = opCall
        XR.UnaryOp(op, parse(x))
      },

      // TODO also need unary operator


      // TODO exclude anything here that's an SqlVariable
      case (ExtractorsDomain.Call.InvokeSqlVariable[Is()]).thenThis { symName ->
        XR.Ident(symName, TypeParser.parse(this.type))
      },

      case(Ir.GetValue[Is()]).thenThis { sym ->
        // Every single instance of this should should be a getSqlVar

        if (!internalVars.contains(sym)) {
          val loc = this.location()
          // TODO Need much longer and better error message (need to say what the clause is)
          error("The symbol `${sym.safeName}` is external. Cannot find it in the symbols-list belonging to the clause ${internalVars.symbols.map { it.safeName }}", loc)
        }

        XR.Ident(sym.safeName, TypeParser.parse(this.type)) // this.symbol.owner.type

        // TODO Need to enhance parseFail to return the failed symbol so later when the
        //      compile-time message is produced we can get the row/column of the bad symbol
        //parseFail("The symbol `${sym.safeName}` is external. Cannot find it in the symbols-list belonging to the clause ${internalVars.symbols.map { it.safeName }}")

      },
      case(Ir.Const[Is()]).thenThis {
        parseConst(this)
      },
      case(Ir.Call.Property[Is(), Is()]).then { expr, name ->
        XR.Property(parse(expr), name)
      },
      case(Ir.Call.FunctionUntethered1[Is()]).thenIfThis { list ->
        this.symbol.safeName == "getSqlVar"
      }.thenThis { arg ->
        val argValue =
          when (arg) {
            is IrConst<*> -> arg.value.toString()
            else -> parseFail("Illegal argument in the `getSqlVar` function:\n${this.dumpKotlinLike()}")
          }

        XR.Ident(argValue, TypeParser.parse(this.type))
      },
      // case(Ir.Call.Function[Is()]).thenIf { (list) -> list.size == 2 }.thenThis { list ->
      //   val a = list.get(0)
      //   val b = list.get(1)
      //   // TODO need to check that's its actually a binary operator!!!
      //   XR.BinaryOp(parse(a), parseSymbol(this.symbol), parse(b))
      // }
      // ,
      case(Ir.Block[Is(), Is()]).then { stmts, ret ->
        XR.Block(stmts.map { parseBlockStatement(it) }, parse(ret))
      },
      case(Ir.When[Is()]).then { cases ->
        val casesAst = cases.map { parseBranch(it) }
        XR.When(casesAst)
      }
    ) ?: parseFail(
      """|======= Could not parse expression from: =======
         |${expr.dumpKotlinLike()}
         |--------- With the Tree ---------
         |${expr.dumpSimple()}
         |
      """.trimMargin())

  context (CompileLogger) fun parseConst(irConst: IrConst<*>): XR =
    if (irConst.value == null) XR.Const.Null
    else when (irConst.kind) {
      IrConstKind.Null -> XR.Const.Null
      IrConstKind.Boolean -> XR.Const.Boolean(irConst.value as kotlin.Boolean)
      IrConstKind.Char -> XR.Const.Char(irConst.value as kotlin.Char)
      IrConstKind.Byte -> XR.Const.Byte(irConst.value as kotlin.Int)
      IrConstKind.Short -> XR.Const.Short(irConst.value as kotlin.Short)
      IrConstKind.Int -> XR.Const.Int(irConst.value as kotlin.Int)
      IrConstKind.Long -> XR.Const.Long(irConst.value as kotlin.Long)
      IrConstKind.String -> XR.Const.String(irConst.value as kotlin.String)
      IrConstKind.Float -> XR.Const.Float(irConst.value as kotlin.Float)
      IrConstKind.Double -> XR.Const.Double(irConst.value as kotlin.Double)
      else -> parseFail("Unknown IrConstKind: ${irConst.kind}")
    }


}
