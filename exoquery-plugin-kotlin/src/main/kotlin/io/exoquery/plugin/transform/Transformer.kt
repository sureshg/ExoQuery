package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.CollectDecls
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

abstract class Transformer {
  // properties that are dependent on ctx need lazy initialization
  abstract val ctx: BuilderContext
  private val logger by lazy { ctx.logger }

  context(BuilderContext, CompileLogger)
  abstract protected fun matchesBase(expression: IrCall): Boolean

  context(ParserContext, BuilderContext, CompileLogger)
  abstract protected fun transformBase(expression: IrCall): IrExpression

  open fun makeParserContext(expression: IrCall): ParserContext {
    val decls = ScopeSymbols(CollectDecls.from(expression)) + ctx.parentScopeSymbols
    return ParserContext(decls, ctx.currentFile)
  }

  fun matches(expression: IrCall): Boolean =
    with(ctx) {
      with (logger) { matchesBase(expression) }
    }

  fun transform(expression: IrCall): IrExpression =
    with(makeParserContext(expression)) {
      with(ctx) {
        with (logger) { transformBase(expression) }
      }
    }
}
