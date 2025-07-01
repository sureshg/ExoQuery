package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.caseEarly
import io.decomat.match
import io.exoquery.SqlAction
import io.exoquery.SqlBatchAction
import io.exoquery.SqlExpression
import io.exoquery.SqlQuery
import io.exoquery.plugin.dispatchArg
import io.exoquery.plugin.isClass
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.trees.*
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

class TransformProjectCapture(val superTransformer: VisitTransformExpressions) : FalliableTransformer<IrExpression>() {

  context(CX.Scope, CX.Builder)
  private fun IrExpression.isContainerOfXR(): Boolean =
    this.type.isClass<SqlExpression<*>>() || this.type.isClass<SqlQuery<*>>() || this.type.isClass<SqlAction<*, *>>() || this.type.isClass<SqlBatchAction<*, *, *>>()

  sealed interface ExprType {
    data object Expr : ExprType
    data object Query : ExprType
    data object Action : ExprType
    data object ActionBatch : ExprType
  }

  context(CX.Scope, CX.Builder, CX.Symbology)
  private fun IrExpression.exprTypeOf(): ExprType? =
    when {
      this.type.isClass<SqlExpression<*>>() -> ExprType.Expr
      this.type.isClass<SqlQuery<*>>() -> ExprType.Query
      this.type.isClass<SqlAction<*, *>>() -> ExprType.Action
      this.type.isClass<SqlBatchAction<*, *, *>>() -> ExprType.ActionBatch // Don't have the uprootable yet
      //else -> parseError("The expression is not a SqlExpression or SqlQuery, (its type is ${this.type.dumpKotlinLike()} which cannot be projected)", this)
      else -> null
    }

  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun matches(expression: IrExpression): Boolean =
    expression.isContainerOfXR()

  context(CX.Scope, CX.Builder, CX.Symbology, CX.QueryAccum)
  override fun transform(expression: IrExpression): IrExpression? {
    // If the type of the expression is not an SqlQuery or SqlExpression then we cannot project so just return
    val exprType = expression.exprTypeOf() ?: return null
    return expression.match(
      // Transform calls to @CapturedFunction so:
      //  @CapturedFunction fun foo(x: SqlQuery<Person>) = select { ... }
      // then later:
      //  capture { 2 + foo(123).use }

      case(Ir.Call[Is()]).thenIf { call -> expression.isContainerOfXR() && call.symbol.owner is IrSimpleFunction }.then { call ->
        val newReciever = if (call.dispatchReceiver != null) { superTransformer.visitExpression(call.dispatchReceiver as IrExpression) } else call.dispatchReceiver
        call.dispatchReceiver = newReciever

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
        call.symbol.owner.match(
          caseEarly(exprType == ExprType.Expr)(Ir.SimpleFunction.withReturnExpression[SqlExpressionExpr.Uprootable[Is()]]).then { (uprootableExpr) ->
            // when propagating forward we don't actually need to deserialize the XR contents
            // of the uprootable, just pass it along into the new instance of SqlExpression(unpackExpr(...), ...)
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.Query)(Ir.SimpleFunction.withReturnExpression[SqlQueryExpr.Uprootable[Is()]]).then { (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.Action)(Ir.SimpleFunction.withReturnExpression[SqlActionExpr.Uprootable[Is()]]).then { (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.ActionBatch)(Ir.SimpleFunction.withReturnExpression[SqlBatchActionExpr.Uprootable[Is()]]).then { (uprootableExpr) ->
            uprootableExpr.replant(expression)
          }
        )
      },
      // TODO the other clauses
      case(Ir.GetField[Is()]).thenIf { expression.isContainerOfXR() }.thenThis { symbol ->
        //val newRecieiver = superTransformer.visitExpression(this.receiver)
        val newExpression = symbol.owner.match(
          // E.g. a class field used in a lift
          //   class Foo { val x = capture { 123 } } which should have become:
          //   val foo = Foo()
          //   capture { 2 + foo.x.use }
          // Which will become:
          //   SqlExpression(Int(2) + Int(123), lifts=foo.x.lifts)

          caseEarly(exprType == ExprType.Expr)(Ir.Field[Is(), SqlExpressionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.Query)(Ir.Field[Is(), SqlQueryExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.Action)(Ir.Field[Is(), SqlActionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.ActionBatch)(Ir.Field[Is(), SqlBatchActionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          }
        )
        newExpression
      },
      case(Ir.GetValue[Is()]).thenIf { expression.isContainerOfXR() }.then { symbol ->
        symbol.owner.match(
          // E.g. something like
          //   val x = capture { 123 + lift(456) }` // i.e. SqlExpression(XR.Int(123), ...)  // (actually it will be serialized so: `val x = SqlExpression(unpackExpr("jksnfksjdnf"), ...)`)
          //   capture { 2 + x.use }
          // Which should become:
          //   SqlExpression(..., lifts=x.lifts)
          //   in more detail:
          //   SqlExpression(Int(2) + Int(123) + ScalarTag(UUID), lifts=x.lifts) // Where x.lifts is ScalarLift(UUID, x) which lives behind the IrVariable("x")
          // so effectively:
          //   capture { 2 + x.use } ->                                                                   // can be thought of something like:
          //     capture { 2 + capture { 123 + lift(x.lifts[UUID_A]) } } ->                               // which is actually:
          //     capture { 2 + SqlExpression(Int(123) + ScalarTag(UUID_A), lifts=ScalarLift(UUID, x)) }   // which becomes:
          //     SqlExpression(Int(2) + SqlExpression(Int(123) + ScalarTag(UUID_A), lifts=ScalarLift(x))) // which then becomes:
          //     SqlExpression(Int(2) + Int(123) + ScalarTag(UUID_A), lifts=ScalarLift(x))
          caseEarly(exprType == ExprType.Expr)(Ir.Variable[Is(), SqlExpressionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.Query)(Ir.Variable[Is(), SqlQueryExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.Action)(Ir.Variable[Is(), SqlActionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          },
          caseEarly(exprType == ExprType.ActionBatch)(Ir.Variable[Is(), SqlBatchActionExpr.Uprootable[Is()]]).then { _, (uprootableExpr) ->
            uprootableExpr.replant(expression)
          }
        )
      }
    ) ?: run {
      // TODO this should be enabled if the user has specified to fail if an uprooting is not possible,
      //      otherwise failure should not happen (e.g. it should be a warning or even info)
      //      and the dynamic code-path should happen instead
      //val msg =
      //  """|----------- Could not project SqlExpression for the expression: -----------
      //     |${expression.dumpKotlinLike()}
      //     |---- With the following IR: ---
      //     |${expression.dumpSimple()}
      //  """.trimMargin() + run {
      //    if (expression is IrDeclarationReference)
      //      """|---- Owner is: ---
      //         |${expression.symbol.owner.dumpKotlinLike()}
      //         |---- With the following IR: ---
      //         |${expression.symbol.owner.dumpSimple()}""".trimMargin()
      //    else
      //      ""
      //  }
      //warn(msg)
      null
    }
  }

  // Diagnostic error for debugging uproot failure of a particular clause
  fun failOwnerUproot(sym: IrSymbol, output: IrExpression): IrExpression {
    error(
      """|----------- Could not uproot the expression: -----------
         |${sym.owner.dumpKotlinLike()}
         |--- With the following IR: ----
         |${sym.owner.dumpSimple()}""".trimMargin()
    )
    return output
  }
}
