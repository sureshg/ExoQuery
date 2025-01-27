package io.exoquery.plugin.settings

import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

val EXO_OPTIONS = CompilerConfigurationKey.create<ExoCompileOptions.Builder>("ExoQuery options")

class ExoCompileOptions(
  val projectBaseDir: File,
  val kotlinOutputDir: File,
  val resourceOutputDir: File,
  val projectDir: String
) {

  class Builder {
    var projectBaseDir: File? = null
    var kotlinOutputDir: File? = null
    var resourceOutputDir: File? = null
    var projectDir: String? = null

    fun build(): ExoCompileOptions {
      return ExoCompileOptions(
        requireNotNull(projectBaseDir) { "A non-null projectBaseDir must be provided" },
        requireNotNull(kotlinOutputDir) { "A non-null kotlinOutputDir must be provided" },
        requireNotNull(resourceOutputDir) { "A non-null resourceOutputDir must be provided" },
        requireNotNull(projectDir) { "A non-null projectDir must be provided" }
      )
    }
  }
}

fun ExoCompileOptions.Builder.processOption(option: ExoCliOption, value: String) = when (option) {
  ExoCliOption.PROJECT_BASE_DIR_OPTION -> projectBaseDir = File(value)
  ExoCliOption.KOTLIN_OUTPUT_DIR_OPTION -> kotlinOutputDir = File(value)
  ExoCliOption.RESOURCE_OUTPUT_DIR_OPTION -> resourceOutputDir = File(value)
  ExoCliOption.PROJECT_DIR_KEY -> projectDir = value
}
