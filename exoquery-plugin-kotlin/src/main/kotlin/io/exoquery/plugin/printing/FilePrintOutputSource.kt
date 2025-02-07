package io.exoquery.plugin.printing

import io.exoquery.plugin.settings.ExoCompileOptions
import io.exoquery.util.Tracer
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

// NOTE if it wasn't for the buffer compilation would be blocked
// on every print-line of the tracer. Want to explore even better solutions
// that use suspended functions.
class FilePrintOutputSource private constructor(val writer: BufferedWriter): Tracer.OutputSource {
  override fun output(str: String) {
    writer.write(str)
  }

  fun close() {
    writer.flush()
    writer.close()
  }

  companion object {
    fun open(options: ExoCompileOptions): FilePrintOutputSource {
      val filePath = Path.of(options.projectDir, "ExoQuery.log")
      val writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      return FilePrintOutputSource(writer)
    }
  }
}
