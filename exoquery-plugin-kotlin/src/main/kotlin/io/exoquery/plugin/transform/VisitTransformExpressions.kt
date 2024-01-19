package io.exoquery.plugin.transform

import com.tylerthrailkill.helpers.prettyprint.pp
import io.exoquery.plugin.trees.ExtractorsDomain
import io.exoquery.plugin.logging.CompileLogger
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
import io.decomat.*
import io.exoquery.plugin.trees.isClass
import io.exoquery.select.SelectClause
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.util.*


/**
 * In cases where someone does something like:
 * ```
 * select {
 *   val p: SqlVariable<Person> = from(Table<Person>)
 *   ...
 * }
 * ```
 * We want the `SqlVariable<Person>` to have a name of `p` since that is
 * the variable that the developer used. Unfortunately at runtime this information
 * is generally not known. What we can do however at compile-time in order to do this is
 * to detect this value of `p` and change the above expression to:
 * ```
 * select {
 *   val p: SqlVariable<Person> = fromAliased(Table<Person>, "p")
 *   ...
 * }
 * ```
 * The only question is how do we know for all possible `from(...)` expressions,
 * what are the `val ...` to be used before them. In order to do that we make a simple
 * transformer that walks through the tree (from outer/left clauses to inner/right ones) and
 * propagates the most-recent `val (x)` that was used. This is carried by the `data:String` element of
 * the `IrElementTransformerWithContext` transformer. The transformer follows two simple rules.
 *
 * 1. When you see an `val (x) = rhs` call recurse into `rhs` with the recorded value `x`.
 * 2. When you encounter SelectClause.from/join/etc...
 *   a. Re-write it to the corresponding aliased one e.g. from -> fromAliased
 *   b. Reset the variable and propagate into the expression inside the from/join/etc... since
 *      there could be sub queries in there.
 */
class VisitPropagateVariables(
  private val context: IrPluginContext,
  private val config: CompilerConfiguration,
  private val projectDir: Path
) : IrElementTransformerWithContext<String?>() {
  // Needs to be lazy or something is not initialized i.e. is null. Maybe one of the constructor values?
  private val ctx by lazy { TransformerOrigin(context, config, this.currentFile, ScopeSymbols.empty) }

  override fun visitCall(expression: IrCall, currVarName: String?): IrElement {
    return if (currVarName != null) {
      val scopeOwner = currentScope!!.scope.scopeOwnerSymbol
      val builderContext = ctx.makeBuilderContext(expression, scopeOwner)
      builderContext.withCtxAndLogger { visitCallLiveVar(expression, currVarName) }
    } else
      super.visitCall(expression, currVarName)
  }

  context(CompileLogger, BuilderContext) fun visitCallLiveVar(expression: IrCall, varName: String): IrElement {
    // NOTE proabably it's a good idea to only propate variable names starting from the SelectClause but
    // the from/join/etc... can Only be inside of a SelectClause so this shouldn't be an issue
    return on(expression).match(
      case(ExtractorsDomain.Call.`from(expr)`[Is(), Is()]).thenThis { reciever, expression ->
        // Recurse inside the from in-case there's a query inside a query
        val newExpression = super.visitExpression(expression, null)
        reciever.callMethod("fromAliased").invoke(newExpression, builder.irString(varName))
      },
      // This should be the catch-all but does it actully work?
      case(Is<Any>()).then {
        super.visitCall(expression, varName)
      }
    ) ?: super.visitCall(expression, varName)
  }

  // TODO if we are not inside of a SelectValue don't do it
  override fun visitVariable(declaration: IrVariable, data: String?): IrStatement {
    // Continue to visit the children of this variable
    val par = declaration.parent

    return when {
      par is IrSimpleFunction && par.extensionReceiverParameter?.type?.isClass<SelectClause<*>>() ?: false -> {
        //ctx.logger.warn("---------- Parent is (${par.extensionReceiverParameter?.type?.classFqName}): --------------\n${declaration.parent.dumpSimple()}\n-------------- Decl Is: -------------\n${declaration.dumpKotlinLike()}\n\n")
        val varName = declaration.name.asString()
        visitDeclaration(declaration, varName)
      }
      else -> {
        // otherwise keep propagating whatever there was before (which should typically be null - the only exception
        // to this are multiple levels of variable declarations inside a SelectClause. Need to think about this
        // case some more.)
        visitDeclaration(declaration, data)
      }
    }
  }
}

class VisitTransformExpressions(
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
    val transformerCtx = TransformerOrigin(context, config, this.currentFile, data)
    val builderContext = transformerCtx.makeBuilderContext(expression, scopeOwner)
    val queryMapTransformer = TransformQueryMap(builderContext, ExtractorsDomain.Call.QueryMap, "mapExpr")
    val queryFlatMapTransformer = TransformQueryFlatMap(builderContext, "flatMapInternal")
    val makeTableTransformer = TransformTableQuery(builderContext)
    val joinOnTransformer = TransformJoinOn(builderContext)

    // TODO Create a 'transformer' for SelectClause that will just collect
    //      the variables declared there and propagate them to clauses defined inside of that

    // TODO Get the variables collected from queryMapTransformer or the transformer that activates
    //      and pass them in when the transformers recurse inside, currently only TransformQueryMap
    //      does this.


    //compileLogger.warn("---------- Call Checking:\n" + expression.dumpKotlinLike())

    val out = when {
      // 1st that that runs here because printed stuff should not be transformed
      // (and this does not recursively transform stuff inside)
      transformPrint.matches(expression) -> {
        transformPrint.transform(expression)
      }

      joinOnTransformer.matches(expression) -> {
        val out = joinOnTransformer.transform(expression, this)
        //logger.warn("----------------- Output ----------------\n" + out.dumpKotlinLike())
        out
      }

      queryFlatMapTransformer.matches(expression) ->
        queryFlatMapTransformer.transform(expression, this)

      makeTableTransformer.matches(expression) -> {
        //compileLogger.warn("=========== Transforming TableQuery ========\n" + expression.dumpKotlinLike())
        makeTableTransformer.transform(expression)
      }

      queryMapTransformer.matches(expression) -> {
        //compileLogger.warn("=========== Transforming Map ========\n" + expression.dumpKotlinLike())
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