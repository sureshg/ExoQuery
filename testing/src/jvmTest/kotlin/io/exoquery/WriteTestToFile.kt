package io.exoquery

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

actual fun writeToFile(fileName: String, contents: String) {
  val filePath = Path.of(fileName)
  val parentDir = filePath.parent
  val fileName = filePath.nameWithoutExtension + "GoldenDynamic.kt"
  val fullPath = parentDir.resolve(fileName)
  Files.write(fullPath, contents.toByteArray())
}
