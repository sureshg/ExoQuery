package io.exoquery.config

import io.exoquery.xr.EncodingXR
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.modules.EmptySerializersModule

fun unpackOptions(str: String) =
  EncodingXR.protoBuf.decodeFromHexString<ExoCompileOptions>(ExoCompileOptions.serializer(), str)


@Serializable
class ExoCompileOptions(
  val generationDir: String,
  val projectSrcDir: String,
  val sourceSetName: String,
  val targetName: String,
  val projectBaseDir: String,
  val kotlinOutputDir: String,
  val resourceOutputDir: String,
  val projectDir: String
) {
  fun encode(): String {
    return EncodingXR.protoBuf.encodeToHexString(ExoCompileOptions.serializer(), this)
  }
}
