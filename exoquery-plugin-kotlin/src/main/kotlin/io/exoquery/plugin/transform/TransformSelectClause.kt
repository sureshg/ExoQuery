package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.exoquery.SX
import io.exoquery.SelectForSX
import io.exoquery.parseError
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.util.tail
import io.exoquery.xr.XR
import io.exoquery.xr.XR.*
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
    val xr = selectClause.toQueryXR()
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

fun SelectForSX.toQueryXR(): XR.Query {
  return TODO()
}

object SelectClauseToXR {
  operator fun invoke(selectClause: SelectForSX): XR.Query {
    // COMPLEX! Need to walk throught the select-clause and recurisvley use `nest` function to nest things in each-other
    val initial = selectClause.from.first()
    val otherFroms = selectClause.from.drop(1)
    nest(initial.xr, initial.variable, otherFroms)

    return TODO()
  }


  /*
  Consider conceptually:
    for {
      x <- from(foo)
      y <- join(bar){stuff}
    } yield (x, y)
  This becomes:
    FlatMap(from(foo), x, FlatMap(join(bar){stuff}, y, Return(Tuple(x, y)))

  However, you can also have multiple froms:
    for {
      x <- from(foo)
      y <- from(bar)
    } yield (x, y)
  This becomes:
    FlatMap(from(foo), x, FlatMap(from(bar), y, Return(Tuple(x, y)))

  Some the `from` clause can also be in tail-position. Now, every single case
  that 'nest' deals with, the from will be in the tail-position.

  Now the most tricky thing that this function does is flip the position of the SX-type clause
  variable (that is on the left) into a FlatMap type variable (that is on the right).
  For example from this:
    val x = foo
    bar(x)
  to this:
    foo.flatMap(x => bar(x))
  which is:
    FlatMap(foo, x, bar(x))
  */
  fun nest(prev: XR.Query, prevVar: XR.Ident, remaining: List<SX>): XR.Query = run {
    if (remaining.isEmpty())
      prev
    else
      // TODO need to work through this and verify
      when (val curr = remaining.first()) {
        // This is not the 1st FROM clause (which will always be in a head-position
        is SX.From ->
          FlatMap(prev, prevVar, nest(curr.xr, curr.variable, remaining.tail))
        is SX.Join ->
          FlatMap(
            prev, prevVar,
            nest(FlatJoin(curr.joinType, curr.onQuery, curr.conditionVariable, curr.condition, curr.loc), curr.variable, remaining.tail)
          )
        is SX.Where ->
          FlatMap(
            prev, prevVar,
            // Since there is no 'new' variable to bind to use use Ident.Unused
            nest(FlatFilter(curr.condition, curr.loc), XR.Ident.Unused, remaining.tail)
          )
        is SX.GroupBy ->
          FlatMap(
            prev, prevVar,
            nest(FlatGroupBy(curr.grouping, curr.loc), XR.Ident.Unused, remaining.tail)
          )
        is SX.SortBy ->
          FlatMap(
            prev, prevVar,
            nest(FlatSortBy(curr.sorting, curr.ordering, curr.loc), XR.Ident.Unused, remaining.tail)
          )
      }
  }


    //when (curr) {
    //  is SX.From -> XR.FlatMap(prev, curr.variable, curr.xr, curr.loc)
    //  is SX.Join -> XR.FlatMap(prev, curr.variable, XR.FlatJoin(curr.joinType, curr.onQuery, curr.conditionVariable, curr.condition, curr.loc))
    //  is SX.Where -> TODO()
    //  is SX.GroupBy -> TODO()
    //  is SX.SortBy -> TODO()
    //}
}


