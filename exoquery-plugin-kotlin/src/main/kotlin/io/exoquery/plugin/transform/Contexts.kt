package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.trees.Lifter
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.IrSymbol

interface LoggableContext {
  val logger: CompileLogger
  companion object {
    fun makeLite(config: CompilerConfiguration, file: IrFile, expr: IrElement) = object: LoggableContext {
      override val logger = CompileLogger(config, file, expr)
    }
  }
}

data class BuilderContext(
  val pluginCtx: IrPluginContext,
  val compilerConfig: CompilerConfiguration,
  val scopeOwner: IrSymbol,
  val currentFile: IrFile,
  val currentExpr: IrElement,
  val transformerScope: TransformerScope
): LoggableContext {
  override val logger = CompileLogger(compilerConfig, currentFile, currentExpr)
  val builder = DeclarationIrBuilder(pluginCtx, scopeOwner, currentExpr.startOffset, currentExpr.endOffset)
  fun makeLifter() = Lifter(this)
}

fun <R> BuilderContext.withCtxAndLogger(f: context(BuilderContext, CompileLogger) () -> R): R = f(this, logger)

data class TransformerOrigin(
  val pluginCtx: IrPluginContext,
  val config: CompilerConfiguration,
  val currentFile: IrFile,
  val parentScopeSymbols: TransformerScope
) {
  fun makeBuilderContext(expr: IrElement, scopeOwner: IrSymbol) =
    BuilderContext(pluginCtx, config, scopeOwner, currentFile, expr, parentScopeSymbols)
}
