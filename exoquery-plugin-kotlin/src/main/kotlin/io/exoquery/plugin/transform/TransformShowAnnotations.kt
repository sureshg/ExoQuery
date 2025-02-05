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
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName


class TransformShowAnnotations(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer<IrCall>() {


  private val fqn: String = "io.exoquery.showAnnotations"

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    expression.symbol.owner.kotlinFqName.asString().let { it == fqn }

  // parent symbols are collected in the parent context
  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression {
    val newExpression = superTransformer.visitExpression(expression)
    val sqlExpressionType = newExpression.type

    val capturedAnnot = sqlExpressionType.getAnnotation(FqName("io.exoquery.Captured")) ?:
      throw IllegalStateException(
        "Cannot find Captured annotation from type: ${sqlExpressionType.dumpKotlinLike()} in the expression:\n${newExpression.dumpKotlinLike()}\nCurrent Annotations are:\n${sqlExpressionType.annotations.map { it.dumpKotlinLike() }.joinToString("\n", "[", "]")}"
      )
    logger.warn("========= Found constructor annot with value: ${capturedAnnot.valueArguments[0]?.dumpKotlinLike()}\nThe full type is:\n${newExpression.type.dumpKotlinLike()}\nOf the expression:\n${newExpression.dumpKotlinLike()}")

    return newExpression
  }
}
