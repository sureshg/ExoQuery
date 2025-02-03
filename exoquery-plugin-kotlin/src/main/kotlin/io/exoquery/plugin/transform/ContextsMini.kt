package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.CompileLogger.Companion.invoke
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile

interface LoggableContext {
  val logger: CompileLogger
  companion object {
    fun makeLite(config: CompilerConfiguration, file: IrFile, expr: IrElement) = object: LoggableContext {
      override val logger = CompileLogger(config, file, expr)
    }
  }
}

interface LocateableContext {
  val currentFile: IrFile
  companion object {
    fun makeLite(file: IrFile) = object: LocateableContext {
      override val currentFile = file
    }
  }
}
