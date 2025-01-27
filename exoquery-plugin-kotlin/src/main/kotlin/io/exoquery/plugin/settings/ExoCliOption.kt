package io.exoquery.plugin.settings

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption

enum class ExoCliOption(
  override val optionName: String,
  override val valueDescription: String,
  override val description: String,
  override val required: Boolean = false,
  override val allowMultipleOccurrences: Boolean = false
) : AbstractCliOption {
  KOTLIN_OUTPUT_DIR_OPTION(
    "kotlinOutputDir",
    "<kotlinOutputDir>",
    "Dir of generated Kotlin sources",
    false
  ),

  RESOURCE_OUTPUT_DIR_OPTION(
    "resourceOutputDir",
    "<resourceOutputDir>",
    "Dir of generated resources",
    false
  ),

  PROJECT_BASE_DIR_OPTION(
    "projectBaseDir",
    "<projectBaseDir>",
    "path to gradle project",
    false
  ),

  PROJECT_DIR_KEY(
    "projectDir",
    "<projectDir>",
    "path to gradle project",
    false
  )
}