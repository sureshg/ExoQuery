package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.CollectDecls
import io.exoquery.plugin.trees.LocationContext
import org.jetbrains.kotlin.ir.expressions.IrExpression

abstract class Transformer<Expr: IrExpression> {
  // properties that are dependent on ctx need lazy initialization
  abstract val ctx: BuilderContext
  private val logger by lazy { ctx.logger }

  context(BuilderContext, CompileLogger)
  abstract protected fun matchesBase(expression: Expr): Boolean

  context(LocationContext, BuilderContext, CompileLogger)
  abstract protected fun transformBase(expression: Expr): IrExpression

  open fun makeLocationContext(expression: Expr): LocationContext {
    val decls = ScopeSymbols(CollectDecls.from(expression)) + ctx.parentScopeSymbols
    return LocationContext(decls, ctx.currentFile)
  }

  fun matches(expression: Expr): Boolean =
    with(ctx) {
      with (logger) { matchesBase(expression) }
    }

  fun transform(expression: Expr): IrExpression =
    with(makeLocationContext(expression)) {
      with(ctx) {
        with (logger) { transformBase(expression) }
      }
    }
}

abstract class FalliableTransformer<Expr: IrExpression> {
  // properties that are dependent on ctx need lazy initialization
  abstract val ctx: BuilderContext
  private val logger by lazy { ctx.logger }

  context(BuilderContext, CompileLogger)
  abstract protected fun matchesBase(expression: Expr): Boolean

  context(LocationContext, BuilderContext, CompileLogger)
  abstract protected fun transformBase(expression: Expr): IrExpression?

  open fun makeLocationContext(expression: Expr): LocationContext {
    val decls = ScopeSymbols(CollectDecls.from(expression)) + ctx.parentScopeSymbols
    return LocationContext(decls, ctx.currentFile)
  }

  fun matches(expression: Expr): Boolean =
    with(ctx) {
      with (logger) { matchesBase(expression) }
    }

  fun transform(expression: Expr): IrExpression? =
    with(makeLocationContext(expression)) {
      with(ctx) {
        with (logger) { transformBase(expression) }
      }
    }
}
