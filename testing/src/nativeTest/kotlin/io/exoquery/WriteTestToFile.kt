package io.exoquery

actual fun writeToFile(originalFilePath: String, newFileName: String, contents: String, override: Boolean) {
  // Don't care about this one, will use the JVM variant to write the dynamic golden files
}
