package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.parseError
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName


class TransformCapturedQuery(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {
  private val fqn: String = "io.exoquery.capture"

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    expression.symbol.owner.kotlinFqName.asString().let { it == fqn }

  // parent symbols are collected in the parent context
  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression {
    val bodyExpr =
      on(expression).match(
        // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
        case(Ir.Call.FunctionUntethered1.Arg[Ir.FunctionExpression.withReturnOnlyBlock[Is()]]).then { (expr) ->
          expr
        }
      )
      ?: parseError("Parsing Failed\n================== The Query-expresson was not a Global Function (with one argument-block): ==================\n" + expression.dumpKotlinLike() + "\n--------------------------\n" + expression.dumpSimple())

    // Transform the contents of `capture { ... }` this is important for several reasons,
    // most notable any kind of variables used inside that need to be inlined e.g:
    // val x = capture { 123 }
    // val y = capture { x.use + 1 } // <- this is what we are transforming
    // Then the `val y` needs to first be transformed into:
    // val y = capture { SqlExpression(XR.Int(123), ...).use + 1 } which will be done by TransformProjectCapture
    // which is called by the superTransformer.visitBlockBody
    val body = superTransformer.visitExpression(bodyExpr)

    // TODO Needs to convey SourceLocation coordinates, think I did this in terpal-sql somehow
    val (xr, dynamics) = Parser.parseQuery(body)
    val paramsExprModel = dynamics.makeParams()
    val newSqlQuery =
      if (dynamics.noRuntimes()) {
        SqlQueryExpr.Uprootable.plantNewUprootable(xr, paramsExprModel)
      } else {
        SqlQueryExpr.Uprootable.plantNewPluckable(xr, dynamics.makeRuntimes(), paramsExprModel)
      }

    //logger.warn("=============== Modified value to: ${capturedAnnot.valueArguments[0]?.dumpKotlinLike()}\n======= Whole Type is now:\n${makeCasted.type.dumpKotlinLike()}")
    //logger.warn("========== Query Output: ==========\n${newSqlQuery.dumpKotlinLike()}")

    return newSqlQuery
  }
}