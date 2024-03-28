package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.decomat.on
import io.exoquery.plugin.buildLocationXR
import io.exoquery.plugin.isClass
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.trees.ExtractorsDomain
import io.exoquery.plugin.trees.Ir
import io.exoquery.select.QueryClause
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
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
@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
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

  override fun visitDeclaration(declaration: IrDeclarationBase, currVarName: String?): IrStatement {
    return when {
      // TODO be sure to check the the FULL PATH of the function
      declaration is IrVariable  -> {
        val scopeOwner = currentScope!!.scope.scopeOwnerSymbol
        val builderContext = ctx.makeBuilderContext(declaration, scopeOwner)
        builderContext.withCtxAndLogger { variableDeclaration(declaration, currVarName) }
      }
      else ->
        super.visitDeclaration(declaration, currVarName)
    }
  }

  context(CompileLogger, BuilderContext) fun variableDeclaration(irVar: IrVariable, currVarName: String?): IrStatement {
    // When the type has an annotation @SqlVar change it's the type of the declaration to @SqlVariable
    // change the initializer fromDirect -> fromAliased (make sure the initializer exists or throw an error)

    // Once all other transforms are done
    // also make a visitor separately that visits every get-call of every type that has an @SqlVar annotation
    // since there should be none left after all transformations (they should all have been turned into XR) throw an error if any exist
    // as a backup we should replaece them all with expressions SqlVariable.invokeHidden() which would throw an error but at least be valid at compile-time
    // that way we will emit errors but the transformations in the AST will at least be valid

    return irVar.match(
      case(Ir.Variable[Is(), ExtractorsDomain.Call.QueryClauseDirectMethod[Is()]]).then { varName, (methodData) ->
        val (caller, args, replacementMethod) = methodData

        // need to recursively transform the arguments e.g. if there are any @QueryClauseAliasedMethod/DirectMethod inside of it
        // for example if it's a `val x = from( query { ... })` and the inner query has variables that also need to be transformed
        val newArgs = args.map { super.visitExpression(it, varName) }
        // also need to visit the caller for the same reason, this is especially important for `val x = join(tbl).on(...)`
        // because the `join` clause needs to be changed for joinAliased in via the visitCallLiveVar visitor here
        val newCaller = caller.transform(this, varName)

        val newCall = newCaller.callMethod(replacementMethod)(*newArgs.toTypedArray())
        irVar.type = newCall.type
        irVar.initializer = newCall
        irVar
      }
    ) ?: run {
      super.visitDeclaration(irVar, currVarName)
    }


    // TODO Once these are detected, find instances of @QueryClauseDirectMethod (e.g. fromDirect) that are not in the expected form i.e. var x = from(Table<Person>) and throw an error
    //      it seems that just replacing the type here makes actual instances of the variable usable (at least via something line println(x)) so need to look into
    //      what is required further. However, if you do something like x.firstName then it will say "class cast exception SqlVariable to Person." That means that
    //      certainly for the sake of sanity at the end of the compiler plugins' running there should be a step to convert x (and all of these variables)
    //      into their SqlVariable.invoke() forms. The interesting question is, what is their type going to be at that time? If it is SqlVariable, how do we know
    //      which ones to add the .invoke() to?
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
        callData.caller.callMethod(callData.replacementMethodToCall).invoke(newExpression, builder.irString(varName), loc)
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