package io.exoquery.plugin

import com.google.auto.service.AutoService
import io.exoquery.plugin.settings.EXO_OPTIONS
import io.exoquery.plugin.settings.ExoCliOption
import io.exoquery.plugin.settings.ExoCompileOptionsBuilder
import io.exoquery.plugin.settings.processOption
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CommandLineProcessor::class)
@OptIn(ExperimentalCompilerApi::class)
class CliProcessor : CommandLineProcessor {
  override val pluginId: String = "io.exoquery.exoquery-plugin"

  override val pluginOptions: Collection<AbstractCliOption> = ExoCliOption.entries

  override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
    if (option !is ExoCliOption) {
      throw CliOptionProcessingException("Unknown option: ${option.optionName}")
    }
    val exoOptions = configuration[EXO_OPTIONS] ?: ExoCompileOptionsBuilder().also { configuration.put(EXO_OPTIONS, it) }
    exoOptions.processOption(option, value)
  }
}
