package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.CompileLogger.Companion.invoke
import io.exoquery.plugin.settings.ExoCompileOptions
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.nameWithoutExtension

object BuildQueryFile {
  operator fun invoke(
    file: IrFile,
    fileScope: TransformerScope,
    config: CompilerConfiguration,
    exoOptions: ExoCompileOptions,
    currentTransformedFile: IrFile // Technically should be the same as `file` but we are not sure about all the edge-cases
  ) {
    val fs = FileSystems.getDefault()
    val srcFileName = Path.of(file.fileEntry.name).nameWithoutExtension + ".queries.sql"
    val packageName = file.packageFqName.asString().replace(".", fs.separator)
    val basePath = exoOptions.resourceOutputDir.toPath()
    val dirOfFile =  Path.of(basePath.toString(), packageName).toFile()
    if (!dirOfFile.exists() && !dirOfFile.mkdirs()) {
      throw IllegalStateException("failed to make parent directories.")
    }
    val srcFile = Path.of(dirOfFile.toString(), srcFileName)
    val queries: String = fileScope.fileQueryAccum.makeFileDump()
    val logger = CompileLogger(config, currentTransformedFile, currentTransformedFile)

    try {
      logger.warn("----------------- Writing Queries to file: ${srcFile.toAbsolutePath()}")
      Files.write(srcFile, queries.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    } catch (e: Exception) {

      logger.error(
        """|Could not write Queries to file: ${srcFile.toAbsolutePath()}
           |================= Error: =================
           |${e.stackTraceToString()}
           |================= Queries
           |${queries}
           """.trimMargin())
    }
  }
}