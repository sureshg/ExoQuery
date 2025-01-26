package io.exoquery.plugin.transform

import io.exoquery.ParseError
import io.exoquery.plugin.location
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import java.nio.file.Path
import org.jetbrains.kotlin.ir.util.*

class VisitTransformExpressions(
  private val context: IrPluginContext,
  private val config: CompilerConfiguration,
  private val projectDir: Path
) : IrElementTransformerWithContext<TransformerScope>() {

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

  override fun visitFileNew(declaration: IrFile, data: TransformerScope): IrFile {
    return super.visitFileNew(declaration, data)
  }


  // TODO move this to visitGetValue? That would be more efficient but what other things might we wnat to transform?
  override fun visitExpression(expression: IrExpression, data: TransformerScope): IrExpression {
    val scopeOwner = currentScope!!.scope.scopeOwnerSymbol
    val transformerCtx = TransformerOrigin(context, config, this.currentFile, data)
    val builderContext = transformerCtx.makeBuilderContext(expression, scopeOwner)
    val transformProjectCapture = TransformProjectCapture(builderContext, this)

    return when {
      transformProjectCapture.matches(expression) ->
        transformProjectCapture.transform(expression) ?: run {
          // In many situations where the capture cannot be projected e.g.
          // val x = if(x) capture { 123 } else capture { 456 } we need to go further into the expression
          // and transform the `capture { ... }` expressions inside into SqlExpession instances. Calling
          // the super-transformer does that.
          super.visitExpression(expression, data)
        }
      else -> super.visitExpression(expression, data)
    }
  }

  override fun visitCall(expression: IrCall, data: TransformerScope): IrElement {

    val scopeOwner = currentScope!!.scope.scopeOwnerSymbol

    val stack = RuntimeException()
    //compileLogger.warn(stack.stackTrace.map { it.toString() }.joinToString("\n"))


    val transformerCtx = TransformerOrigin(context, config, this.currentFile, data)
    val builderContext = transformerCtx.makeBuilderContext(expression, scopeOwner)

    val transformPrint = TransformPrintSource(builderContext, this)
    // TODO just for Expression capture or also for Query capture? Probably both
    val transformCapture = TransformCapturedExpression(builderContext, this)
    val transformCaptureQuery = TransformCapturedQuery(builderContext, this)
    val transformSelectClause = TransformSelectClause(builderContext, this)
    // I.e. tranforms the SqlQuery.build call (i.e. the SqlQuery should have already been transformed into an Uprootable before recursing "out" to the .build call
    // or the .build call should have recursed down into it (because it calls the superTransformer on the reciever of the .build call)
    val transformCompileQuery = TransformCompileQuery(builderContext, this)

    // TODO Catch parser errors here, make a warning via the compileLogger (in the BuilderContext) & don't transform the expresison

    // TODO Create a 'transformer' for SelectClause that will just collect
    //      the variables declared there and propagate them to clauses defined inside of that

    // TODO Get the variables collected from queryMapTransformer or the transformer that activates
    //      and pass them in when the transformers recurse inside, currently only TransformQueryMap
    //      does this.


    //compileLogger.warn("---------- Call Checking:\n" + expression.dumpKotlinLike())
    fun parseExpression() =
      when {

        // 1st that that runs here because printed stuff should not be transformed
        // (and this does not recursively transform stuff inside)
        transformPrint.matches(expression) -> transformPrint.transform(expression)
        // NOTE the .matches function should just be a cheap match on the expression, not a full extractionfalse
        transformCapture.matches(expression) -> transformCapture.transform(expression)
        transformCaptureQuery.matches(expression) -> transformCaptureQuery.transform(expression)
        transformSelectClause.matches(expression) -> transformSelectClause.transform(expression)
        // Is this an sqlQuery.build(PostgresDialect) call? if yes see if the it is a compile-time query and transform it
        transformCompileQuery.matches(expression) -> transformCompileQuery.transform(expression)

        //showAnnotations.matches(expression) -> showAnnotations.transform(expression)

        // Want to run interpolator invoke before other things because the result of it is an SqlExpression that will
        // the be re-parsed in the parser if it is inside of a context(EnclosedContext) e.g. Query.map
        else ->
          // No additional data (i.e. Scope-Symbols) to add since none of the transformers was activated
          super.visitCall(expression, data)
      }

    val out = try {
      parseExpression()
    } catch (e: ParseError) {
      builderContext.logger.error(e.msg, e.location ?: expression.location(currentFile.fileEntry))
      expression
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
