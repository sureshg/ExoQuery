package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.trees.Lifter
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol

data class BuilderContext(
  val pluginCtx: IrPluginContext,
  val compilerConfig: CompilerConfiguration,
  val scopeOwner: IrSymbol,
  val currentFile: IrFile,
  val currentExpr: IrExpression
) {
  val logger = CompileLogger(compilerConfig)
  val builder = DeclarationIrBuilder(pluginCtx, scopeOwner, currentExpr.startOffset, currentExpr.endOffset)
  val lifter = Lifter(builder, pluginCtx, logger)
}

data class TransformerOrigin(
  val pluginCtx: IrPluginContext,
  val config: CompilerConfiguration,
  val scopeOwner: IrSymbol,
  val currentFile: IrFile,
  val parentScopeSymbols: ScopeSymbols
) {
  fun makeBuilderContext(expr: IrExpression) =
    BuilderContext(pluginCtx, config, scopeOwner, currentFile, expr)
}
