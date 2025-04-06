package io.exoquery

actual fun writeToFile(originalFilePath: String, newFileName: String, contents: String, override: Boolean): Unit {
  // Only want to write to files on the JVM which we use to create expected-golden files.
  // For other dialects just read them and test
}
