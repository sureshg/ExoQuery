package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.settings.ExoCompileOptions
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrFile
import java.io.File
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
  val logger = CompileLogger(compilerConfig, currentTransformedFile, currentTransformedFile)
  val queries: String by lazy { codeFileScope.fileQueryAccum.makeFileDump() }

  fun buildRegular() {
    // e.g: /home/me/project/src/commonMain/com/someplace/Code.kt -> /home/me/project/src/commonMain/kotlin/com/someplace/
    val codeFileParent = Path.of(codeFile.fileEntry.name).parent
    // projectDir: /home/me/project/
    val projectDirPath = Path.of(config.projectDir)
    val (pathOfFile, regularProcess) =
      if (codeFileParent.startsWith(projectDirPath)) {
        val trimmedPath =
          projectDirPath.relativize(codeFileParent) // this should be: src/commonMain/kotlin/com/someplace/
            // we expect it to start with src so remove that
            .let { if (it.startsWith("src")) Path.of("src").relativize(it) else it } // now should be: commonMain/kotlin/com/someplace/
        // fileGenerationPath: /home/me/project/build/generated/exo
        val fileGenerationPath = Path.of(config.generationDir)
        // outputPath: /home/me/project/build/generated/exo/ + src/commonMain/kotlin/com/someplace/
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

    if (!dirOfFile.dirExistsOrCouldMake()) return // failing the build, return without creating the file

    val srcFile = Path.of(dirOfFile.toString(), srcFileName)

    writeToFile(srcFile)
  }

  fun buildForResources() {
    // e.g: /home/me/project/src/commonMain/com/someplace/Code.kt -> /home/me/project/src/commonMain/com/someplace/
    val codeFileParent = Path.of(codeFile.fileEntry.name).parent
    // projectDir: /home/me/project/
    val projectDirPath = Path.of(config.projectDir)
    if (!codeFileParent.startsWith(projectDirPath)) {
      logger.warn("In order to be able to generate queries in the resources directory, the file must be in the project source directory: ${projectDirPath} but it was in: ${codeFileParent}")
      return
    }

    val trimmedPath =
      projectDirPath.relativize(codeFileParent) // this should be: src/commonMain/kotlin/com/someplace/
        // we expect it to start with src so remove that
        .let { if (it.startsWith("src")) Path.of("src").relativize(it) else it } // now should be: commonMain/kotlin/com/someplace/
    // TODO should probably stop here if src could not be found since we'll expect an exact directory structure for it when we loaded it via the resource path

    val segments = trimmedPath.iterator().asSequence().toList()
    // i.e. commonMain
    val platformSourceDir = segments[0]
    // i.e. kotlin
    val sourceDir = segments[1]
    // todo if it is not "kotlin" probably need to make an assertion-error at this point

    // fileGenerationPath: /home/me/project/ + src/ + resources/ + commonMain/ + com/someplace/
    val fileGenerationPath =
      Path.of(config.projectDir)
        .resolve("src")
        .resolve(platformSourceDir)
        .resolve("resources")
        .resolve(segments.drop(2).joinToString(fs.separator)) // com/ + someplace/ -> com/someplace/

    val dirOfFile = fileGenerationPath.toFile()

    val srcFileName = Path.of(codeFile.fileEntry.name).nameWithoutExtension + ".queries.sql"

    if (!dirOfFile.dirExistsOrCouldMake()) return // failing the build, return without creating the file

    val srcFile = Path.of(dirOfFile.toString(), srcFileName)
    writeToFile(srcFile)
  }

  private fun File.dirExistsOrCouldMake() =
    if (!this.exists() && !this.mkdirs()) {
      logger.error("Failed to create the parent directory: ${this.absolutePath}")
      false
    } else {
      true
    }

  fun writeToFile(srcFile: Path) {
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