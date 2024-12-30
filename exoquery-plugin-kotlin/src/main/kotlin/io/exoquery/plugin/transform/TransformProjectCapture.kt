package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.exoquery.SqlExpression
import io.exoquery.plugin.isClass
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.ParserContext
import io.exoquery.plugin.trees.SqlExpressionExpr
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetClass
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName

class TransformProjectCapture(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrExpression>() {
  context(BuilderContext, CompileLogger)

  override fun matchesBase(expression: IrExpression): Boolean =
    expression.type.isClass<SqlExpression<*>>()
      //&& expression is IrGetValue

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrExpression): IrExpression =
    expression.match(
      case(Ir.Call[Is()]).thenIf { call -> expression.type.isClass<SqlExpression<*>>() && call.symbol.owner is IrSimpleFunction /* do the cheaper check here */ }.then { call ->
        //error("--------------- GOT HERE --------------\n${call.symbol.owner.dumpSimple()}")
        call.symbol.owner.match(
          // For example:
          //   fun foo(x: Int) = capture { 1 + lift(x) } // i.e. SqlExpression(Int(1) + ScalarTag(UUID), lifts=ScalarLift(UUID,x))
          //   capture { 2 + foo(123).use }
          // We want to project SqlExpresion ontot he foo(123) so it becomes something like:
          //   capture { 2 + capture { 1 + lift(x) } }    i.e:
          //   SqlExpression(xr=Int(2) + SqlExpression(xr=Int(1) + ScalarTag(UUID), lifts=ScalarLift(x))    to simplify:
          //   SqlExpression(..., SqlExpression(..., lifts=ScalarLift(x)))
          // Now since `x` is a variable that only exists in `foo` we actually need to do this:
          //   SqlExpression(..., SqlExpression(..., lifts=foo(123).lifts))
          // Which oddly would look something like:
          //   capture { 2 + capture { 1 + foo(123).lifts } }
          // All of this works because in order to the output for `foo` to be uprootable in the first place, it's AST
          // has to effectively be static. Anything known only at runtime could only be found in the lifts
          // (also the value of runtimes needs to be `Empty` once they are implemented)
          case(Ir.SimpleFunction.withReturnExpression[SqlExpressionExpr.Uprootable[Is()]]).then { (uprootableExpr) ->
            uprootableExpr.replant(expression)
          }
        )
      },
      case(Ir.GetValue[Is()]).thenIf { expression.type.isClass<SqlExpression<*>>() }.then { symbol ->
        symbol.owner.match(
          // E.g. `val x = capture { 123 }` which should have become:
          // `val x = SqlExpression(XR.Int(123), ...)
          // (actuall it will be serialized so: `val x = SqlExpression(unpackExpr("jksnfksjdnf"), ...)`)
          // So it will be Uprootable(XR.Int(123))
          case(Ir.Variable[Is(), SqlExpressionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            // when propagating forward we don't actually need to deserialize the XR contents
            // of the uprootable, just pass it along into the new instance of SqlExpression(unpackExpr(...), ...)
            uprootableExpr.replant(expression)
          }
        ) ?: run {
          error("""
              ----------- Could not uproot the expression: -----------
              ${symbol.owner.dumpKotlinLike()}
              With the following IR:
              ${symbol.owner.dumpSimple()}
            """.trimIndent())
          expression
        }
      }
    ) ?: run {
      error("""
        ----------- Could not project SqlExpression for the expression: -----------
        ${expression.dumpKotlinLike()}
        With the following IR:
        ${expression.dumpSimple()}
      """.trimIndent())
      expression
    }
}