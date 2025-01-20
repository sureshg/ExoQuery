package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.exoquery.parseError
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName


class TransformSelectClause(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {
  private val fqn: String = "io.exoquery.select"

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    expression.symbol.owner.kotlinFqName.asString().let { it == fqn }

  // parent symbols are collected in the parent context
  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(selectExpressionRaw: IrCall): IrExpression {
    // since there could be SqlQuery clauses inside we need to recurisvely transform the stuff inside the Select-Clause first
    // therefore we need to call the super-transform on the select lambda
    val selectLambda =
      selectExpressionRaw.match(
        case(Ir.Call.FunctionUntethered1[Is("io.exoquery.select"), Is()]).then { name, selectLambdaRaw ->
          superTransformer.visitExpression(selectLambdaRaw, ScopeSymbols.empty)
        }
        // TODO use Messages.kt, use better example
      ) ?: parseError("Parsing Failed\n================== The Select-expresson was not a Global Function (with one argument-block): ==================\n" + selectExpressionRaw.dumpKotlinLike() + "\n--------------------------\n" + selectExpressionRaw.dumpSimple())

    val (selectClause, dynamics) = Parser.parseSelectClauseLambda(selectLambda)
    // Store the selectClause inside a XR.CustomQueryRef instance so that we can invoke the XR serialization and "plant" it into the IR
    // This way we can directly expose this SelectClause for much simpler testing and deal with needing to transform it into XR later
    val xr = XR.CustomQueryRef(selectClause)
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



