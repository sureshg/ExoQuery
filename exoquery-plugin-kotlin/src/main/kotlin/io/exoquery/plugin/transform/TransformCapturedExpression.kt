package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.parseError
import io.exoquery.plugin.locationXR
import io.exoquery.xr.XR
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.dumpSimple
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import io.exoquery.plugin.trees.CallData.MultiArgMember.ArgType
import io.exoquery.xr.XRType
import io.exoquery.xr.encode
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBuf.Default.serializersModule
import kotlinx.serialization.serializer
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName


class TransformCapturedExpression(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {
  private val fqn: String = "io.exoquery.captureValue"

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    expression.symbol.owner.kotlinFqName.asString().let { it == fqn }

  // parent symbols are collected in the parent context
  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression {
    val bodyRaw =
      on(expression).match(
        // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
        case(io.exoquery.plugin.trees.Ir.Call.FunctionUntethered1.Arg[io.exoquery.plugin.trees.Ir.FunctionExpression.withBlock[Is(), Is()]]).then { (_, body) ->
          body
        }
      )
      ?: parseError("Parsing Failed\n================== The expresson was not a Global Function (with one argument-block): ==================\n" + expression.dumpKotlinLike() + "\n--------------------------\n" + expression.dumpSimple())

    // Transform the contents of `capture { ... }` this is important for several reasons,
    // most notable any kind of variables used inside that need to be inlined e.g:
    // val x = capture { 123 }
    // val y = capture { x.use + 1 } // <- this is what we are transforming
    // Then the `val y` needs to first be transformed into:
    // val y = capture { SqlExpression(XR.Int(123), ...).use + 1 } which will be done by TransformProjectCapture
    // which is called by the superTransformer.visitBlockBody
    val body = superTransformer.visitBlockBody(bodyRaw, ScopeSymbols.empty) as IrBlockBody

    // TODO Needs to convey SourceLocation coordinates, think I did this in terpal-sql somehow
    val (xr, dynamics) = Parser.parseFunctionBlockBody(body)

    val xrExpr = xr as? XR.Expression ?: parseError("Could not parse to expression:\n${xr}") // TODO better print

    val paramsExprModel = dynamics.makeParams()
    //val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr, paramsListExpr))
    //val makeCasted = builder.irImplicitCast(make, expression.type)
    val newSqlExpression =
      if (dynamics.noRuntimes()) {
        SqlExpressionExpr.Uprootable.plantNewUprootable(xrExpr, paramsExprModel)
      } else {
        SqlExpressionExpr.Uprootable.plantNewPluckable(xrExpr, dynamics.makeRuntimes(), paramsExprModel)
      }

    //logger.warn("=============== Modified value to: ${capturedAnnot.valueArguments[0]?.dumpKotlinLike()}\n======= Whole Type is now:\n${makeCasted.type.dumpKotlinLike()}")
    //logger.warn("========== Output: ==========\n${newSqlExpression.dumpKotlinLike()}")

    // maybe get rid of @Capture by copying (i.e. splicing since it's presence might tell us where we don't want to splice?)
    // alternatively would need to account for the annotation in the ExprModel?

    return newSqlExpression
  }
}