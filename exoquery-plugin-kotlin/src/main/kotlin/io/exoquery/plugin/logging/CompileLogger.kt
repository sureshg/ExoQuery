package io.exoquery.plugin.logging

import io.exoquery.plugin.location
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation

typealias Location = CompilerMessageSourceLocation

data class CompileLogger(val messageCollector: MessageCollector, val currentFile: IrFileEntry, val macroInvokeSite: IrElement) {
  fun warn(msg: String) =
    messageCollector.report(CompilerMessageSeverity.WARNING, msg, macroInvokeSite.location(currentFile))

  fun warn(msg: String, loc: Location) =
    messageCollector.report(CompilerMessageSeverity.WARNING, msg, loc)

  // TODO need a "lesser thing" here than parser context, just need to know the current file so need to make a context with that
  context(ParserContext) fun warn(msg: String, elem: IrElement) =
    messageCollector.report(CompilerMessageSeverity.WARNING, msg, elem.location())

  fun error(msg: String) =
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, macroInvokeSite.location(currentFile))

  fun error(msg: String, loc: Location) {
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, loc)
  }

  context(ParserContext) fun error(msg: String, elem: IrElement) {
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, elem.location())
  }

  fun currentLocation() = macroInvokeSite.location(currentFile)

  companion object {
    operator fun invoke(config: CompilerConfiguration, currentFile: IrFileEntry, macroInvokeSite: IrElement) =
      config.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE).let {
        CompileLogger(it, currentFile, macroInvokeSite)
      }
  }
}