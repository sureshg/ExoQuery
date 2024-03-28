package io.exoquery.plugin.logging

import io.exoquery.plugin.location
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation

data class CompileLogger(val messageCollector: MessageCollector, val currentFile: IrFileEntry, val macroInvokeSite: IrElement) {
  fun warn(msg: String) =
    messageCollector.report(CompilerMessageSeverity.WARNING, msg, macroInvokeSite.location(currentFile))

  fun error(msg: String) =
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, macroInvokeSite.location(currentFile))

  fun error(msg: String, loc: CompilerMessageSourceLocation) {
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, loc)
  }

  fun currentLocation() = macroInvokeSite.location(currentFile)

  companion object {
    operator fun invoke(config: CompilerConfiguration, currentFile: IrFileEntry, macroInvokeSite: IrElement) =
      config.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE).let {
        CompileLogger(it, currentFile, macroInvokeSite)
      }
  }
}