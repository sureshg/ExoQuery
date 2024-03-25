package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.plugin.buildLocationXR
import io.exoquery.plugin.isClass
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.trees.ExtractorsDomain
import io.exoquery.select.QueryClause
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import java.nio.file.Path

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
      case(ExtractorsDomain.Call.QueryClauseAliasedMethod[Is()]).thenThis { callData ->
        // Recurse inside the from in-case there's a query inside a query
        // Note right now it only supports one argument but we can easily extend to multiple
        val expr = callData.args.first()
        val newExpression = super.visitExpression(expr, null)
        val loc = makeLifter().liftLocation(expr.buildLocationXR())
        callData.caller.callMethod(callData.newMethod).invoke(newExpression, builder.irString(varName), loc)
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
      par is IrSimpleFunction && par.extensionReceiverParameter?.type?.isClass<QueryClause<*>>() ?: false -> {
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