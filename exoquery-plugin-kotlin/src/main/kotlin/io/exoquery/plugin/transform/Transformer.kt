package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.CollectDecls
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

abstract class Transformer<Expr: IrExpression> {
  // properties that are dependent on ctx need lazy initialization
  abstract val ctx: BuilderContext
  private val logger by lazy { ctx.logger }

  context(BuilderContext, CompileLogger)
  abstract protected fun matchesBase(expression: Expr): Boolean

  context(ParserContext, BuilderContext, CompileLogger)
  abstract protected fun transformBase(expression: Expr): IrExpression

  open fun makeParserContext(expression: Expr): ParserContext {
    val decls = ScopeSymbols(CollectDecls.from(expression)) + ctx.parentScopeSymbols
    return ParserContext(decls, ctx.currentFile)
  }

  fun matches(expression: Expr): Boolean =
    with(ctx) {
      with (logger) { matchesBase(expression) }
    }

  fun transform(expression: Expr): IrExpression =
    with(makeParserContext(expression)) {
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

  context(ParserContext, BuilderContext, CompileLogger)
  abstract protected fun transformBase(expression: Expr): IrExpression?

  open fun makeParserContext(expression: Expr): ParserContext {
    val decls = ScopeSymbols(CollectDecls.from(expression)) + ctx.parentScopeSymbols
    return ParserContext(decls, ctx.currentFile)
  }

  fun matches(expression: Expr): Boolean =
    with(ctx) {
      with (logger) { matchesBase(expression) }
    }

  fun transform(expression: Expr): IrExpression? =
    with(makeParserContext(expression)) {
      with(ctx) {
        with (logger) { transformBase(expression) }
      }
    }
}
