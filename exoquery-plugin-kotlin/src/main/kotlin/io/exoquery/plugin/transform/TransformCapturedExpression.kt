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
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName


class TransformCapturedExpression(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer() {


  private val fqn: String = "io.exoquery.capture"

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    expression.symbol.owner.kotlinFqName.asString().let { it == fqn }

  // parent symbols are collected in the parent context
  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression {
//    val body =
//      on(expression).match(
//        // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
//        case(io.exoquery.plugin.trees.Ir.Call.FunctionUntethered1[io.exoquery.plugin.trees.Ir.FunctionExpression.withBlock[Is(), Is()]]).then { (_, body) ->
//          body
//        }
//      )
//      ?: parseError("Parsing Failed\n================== The expresson was not a Global Function (with one argument-block): ==================\n" + expression.dumpKotlinLike() + "\n--------------------------\n" + expression.dumpSimple())
//
//    // TODO Needs to convey SourceLocation coordinates, think I did this in terpal-sql somehow
//    val (xr, dynamics) = Parser.parseFunctionBlockBody(body)

    val classId = ClassId.topLevel(FqName("io.exoquery.SqlExpression"))
    val sqlExpressionCtor = pluginCtx.referenceConstructors(classId).firstOrNull() ?: throw IllegalStateException("Cannot find a constructor for: ${classId} for SqlExpression")
    //val sqlExpressionType = sqlExpressionCtor.owner.returnType // don't see the type annotation on the type the io.exoquery.SqlExpression function-return
    // because obviously it doesn't have an annotation because it's an SqlExpression constructor but the `capture` expression output should have one because that's how the `capture` method is typed
    val sqlExpressionType = expression.type



    val capturedAnnot = sqlExpressionType.getAnnotation(FqName("io.exoquery.annotation.Captured")) ?:
    throw IllegalStateException(
      "Cannot find Captured annotation from type: ${sqlExpressionType.dumpKotlinLike()} in the expression:\n${expression.dumpKotlinLike()}\nCurrent Annotations are:\n${sqlExpressionType.annotations.map { it.dumpKotlinLike() }.joinToString("\n", "[", "]")}"
    )
    capturedAnnot.putValueArgument(0, builder.irString("modified-value"))






    val make = makeClassFromString("io.exoquery.SqlExpression", listOf(builder.irString("random-stuff")), listOf())
    val makeCasted = builder.irAs(make, sqlExpressionType)

    logger.warn("=============== Modified value to: ${capturedAnnot.valueArguments[0]?.dumpKotlinLike()}\n======= Whole Type is now:\n${makeCasted.type.dumpKotlinLike()}")

    return makeCasted
  }
}