package io.exoquery.plugin.transform

import io.exoquery.annotation.ExoGoldenOverride
import io.exoquery.annotation.ExoGoldenTest
import io.exoquery.plugin.hasAnnotation
import io.exoquery.plugin.location
import io.exoquery.plugin.locationLite
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.CompileLogger.Companion.invoke
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

object QueryFileBuilder {
  sealed interface OutputMode {
    object Regular : OutputMode
    object GoldenOverwrite : OutputMode
    object GoldenNoOverwrite : OutputMode
  }

  operator fun invoke(queryFile: QueryFile) {
    if (!queryFile.codeFileScope.hasQueries()) return

    val resourcesWrite =
      if (queryFile.codeFile.hasAnnotation<ExoGoldenOverride>())
        OutputMode.GoldenOverwrite
      else if (queryFile.codeFile.hasAnnotation<ExoGoldenTest>())
        OutputMode.GoldenNoOverwrite
      else
        null

    // either way write the queries out to the build directory
    writeFile(OutputMode.Regular, queryFile)
    // if the resourcesWrite is defined, write the queries out to the resources directory
    if (resourcesWrite != null) {
      writeFile(resourcesWrite, queryFile)
    }
  }

  private fun writeFile(outputMode: OutputMode, queryFile: QueryFile) =
    when (outputMode) {
      OutputMode.Regular -> queryFile.buildRegular()
      OutputMode.GoldenOverwrite -> queryFile.buildForGoldenFile(overrwriteExisting = true)
      OutputMode.GoldenNoOverwrite -> queryFile.buildForGoldenFile(overrwriteExisting = false)
    }
}