package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.settings.ExoCompileOptions
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.nameWithoutExtension

class BuildQueryFile(
  val codeFile: IrFile,
  val codeFileScope: TransformerScope,
  val compilerConfig: CompilerConfiguration,
  val config: ExoCompileOptions,
  val currentTransformedFile: IrFile // Technically should be the same as `file` but we are not sure about all the edge-cases
) {
  val fs = FileSystems.getDefault()

  fun buildRegular() {
    // e.g: /home/me/project/src/commonMain/com/someplace/Code.kt -> /home/me/project/src/commonMain/com/someplace/
    val codeFileParent = Path.of(codeFile.fileEntry.name).parent
    // projectDir: /home/me/project/
    val projectDirPath = Path.of(config.projectDir)
    val (pathOfFile, regularProcess) =
      if (codeFileParent.startsWith(projectDirPath)) {
        val trimmedPath =
          projectDirPath.relativize(codeFileParent) // this should be: src/commonMain/com/someplace/
            // we expect it to start with src so remove that
            .let { if (it.startsWith("src")) Path.of("src").relativize(it) else it } // now should be: commonMain/com/someplace/
        // fileGenerationPath: /home/me/project/build/generated/exo
        val fileGenerationPath = Path.of(config.generationDir)
        // outputPath: /home/me/project/build/generated/exo/ + src/commonMain/com/someplace/
        val outputPath = fileGenerationPath.resolve(trimmedPath)
        outputPath to true
      } else {
        val packageName = codeFile.packageFqName.asString().replace(".", fs.separator)
        // fileGenerationPath: /home/me/project/build/generated/exo
        val fileGenerationPath = Path.of(config.generationDir)
        // fileGenerationPath: /home/me/project/build/generated/exo/linuxX64/linuxX64Main/com/someplace/
        val outputPath =  fileGenerationPath.resolve(config.targetName).resolve(config.sourceSetName).resolve(Path.of(packageName))
        outputPath to false
      }
    val dirOfFile = pathOfFile.toFile()

    val srcFileName = Path.of(codeFile.fileEntry.name).nameWithoutExtension + ".queries.sql"

    if (!dirOfFile.exists() && !dirOfFile.mkdirs()) {
      throw IllegalStateException("failed to make parent directories.")
    }
    val srcFile = Path.of(dirOfFile.toString(), srcFileName)
    val queries: String = codeFileScope.fileQueryAccum.makeFileDump()
    val logger = CompileLogger(compilerConfig, currentTransformedFile, currentTransformedFile)

    try {
      val addition = if (regularProcess) "" else " (Could not regular file path, needed to use default-source set)"
      logger.warn("----------------- Writing Queries to file${addition}: ${srcFile.toAbsolutePath()}")
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