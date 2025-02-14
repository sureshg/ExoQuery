package io.exoquery

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

actual fun writeToFile(originalFilePath: String, newFileName: String, contents: String) {
  val parentDir = Path.of(originalFilePath).parent
  val fullPath = parentDir.resolve(newFileName + ".kt")
  Files.write(fullPath, contents.toByteArray())
}
