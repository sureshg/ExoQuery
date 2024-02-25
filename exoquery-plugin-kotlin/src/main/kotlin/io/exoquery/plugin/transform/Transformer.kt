package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
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
  abstract protected fun transformBase(expression: IrCall, superTransformer: VisitTransformExpressions): IrExpression

  open fun makeParserContext(): ParserContext = ParserContext(ctx.parentScopeSymbols, ctx.currentFile)

  fun matches(expression: IrCall): Boolean =
    with(ctx) {
      with (logger) { matchesBase(expression) }
    }

  fun transform(expression: IrCall, superTransformer: VisitTransformExpressions): IrExpression =
    with(makeParserContext()) {
      with(ctx) {
        with (logger) { transformBase(expression, superTransformer) }
      }
    }
}
