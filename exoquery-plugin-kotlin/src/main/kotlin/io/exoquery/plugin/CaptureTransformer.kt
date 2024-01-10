package io.exoquery.plugin

import com.tylerthrailkill.helpers.prettyprint.pp
import io.exoquery.plugin.trees.ExtractorsDomain
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.transform.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import java.nio.file.Path

class CaptureTransformer(
  private val context: IrPluginContext,
  private val config: CompilerConfiguration,
  private val projectDir: Path
) : IrElementTransformerWithContext<ScopeSymbols>() {

  val logger = CompileLogger(config)

  // currentFile is not initialized here yet or something???
  //val sourceFinder: FindSource = FindSource(this.currentFile, projectDir)

  private fun typeIsFqn(type: IrType, fqn: String): Boolean {
    if (type !is IrSimpleType) return false

    return when (val owner = type.classifier.owner) {
      is IrClass -> owner.kotlinFqName.asString() == fqn
      else -> false
    }
  }

//  override fun visitExpression(expression: IrExpression): IrExpression {
//    val compileLogger = CompileLogger(config)
//    compileLogger.warn("---------- Expression Checking:\n" + expression.dumpKotlinLike())
//    return super.visitExpression(expression)
//  }

  override fun visitCall(expression: IrCall, data: ScopeSymbols): IrElement {

    val scopeOwner = currentScope!!.scope.scopeOwnerSymbol
    val compileLogger = CompileLogger(config)

    val stack = RuntimeException()
    //compileLogger.warn(stack.stackTrace.map { it.toString() }.joinToString("\n"))


    val transformPrint = TransformPrintSource(context, config, scopeOwner)
    val transformerCtx = TransformerOrigin(context, config, scopeOwner, this.currentFile, data)
    val queryMapTransformer = TransformQueryMap(transformerCtx, ExtractorsDomain.Call.QueryMap, "mapExpr")
    val queryFlatMapTransformer = TransformQueryFlatMap(transformerCtx, "flatMapInternal")
    val makeTableTransformer = TransformTableQuery(transformerCtx)

    // TODO Create a 'transformer' for SelectClause that will just collect
    //      the variables declared there and propagate them to clauses defined inside of that

    // TODO Get the variables collected from queryMapTransformer or the transformer that activates
    //      and pass them in when the transformers recurse inside, currently only TransformQueryMap
    //      does this.


    //compileLogger.warn("---------- Call Checking:\n" + expression.dumpKotlinLike())

    val out = when {
      queryFlatMapTransformer.matches(expression) ->
        queryFlatMapTransformer.transform(expression, this)

      makeTableTransformer.matches(expression) -> {
        //compileLogger.warn("=========== Transforming TableQuery ========\n" + expression.dumpKotlinLike())
        makeTableTransformer.transform(expression)
      }

      transformPrint.matches(expression) -> {
        transformPrint.transform(expression)
      }

      queryMapTransformer.matches(expression) -> {
        compileLogger.warn("=========== Transforming Map ========\n" + expression.dumpKotlinLike())
        queryMapTransformer.transform(expression, this)
      }

      else ->
        // No additional data (i.e. Scope-Symbols) to add since none of the transformers was activated
        super.visitCall(expression, data)
    }
    return out
  }
}




/** Finds the line and column of [irElement] within this file. */
fun IrFile.locationOf(irElement: IrElement?): CompilerMessageSourceLocation {
  val sourceRangeInfo = fileEntry.getSourceRangeInfo(
    beginOffset = irElement?.startOffset ?: 0,
    endOffset = irElement?.endOffset ?: 0
  )
  return CompilerMessageLocationWithRange.create(
    path = sourceRangeInfo.filePath,
    lineStart = sourceRangeInfo.startLineNumber + 1,
    columnStart = sourceRangeInfo.startColumnNumber + 1,
    lineEnd = sourceRangeInfo.endLineNumber + 1,
    columnEnd = sourceRangeInfo.endColumnNumber + 1,
    lineContent = null
  )!!
}

fun <T> T.pprint(
  indent: Int = 2,
  wrappedLineWidth: Int = 80
): String {
  val buff = StringBuffer()
  pp(this, indent, buff, wrappedLineWidth)
  return buff.toString()
}