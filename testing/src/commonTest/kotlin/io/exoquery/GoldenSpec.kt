package io.exoquery

import io.exoquery.annotation.DslExt
import io.exoquery.printing.PrintableValue
import io.exoquery.printing.QueryFileKotlinMaker
import io.exoquery.xr.XR
import io.kotest.core.spec.DslDrivenSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import kotlin.test.assertEquals

sealed interface Mode {
  val fileName: String

  data class ExoGoldenTest(override val fileName: String) : Mode
  data class ExoGoldenOverride(override val fileName: String): Mode
  companion object {
    // TODO add a parameter for package so tests can be in other packages then io.exoquery
    @DslExt
    fun ExoGoldenTest(): Mode = ExoGoldenTest(errorCap("No file name provided. This should be overridden by the compiler-plugin to ExoGoldenTestExpr"))
    fun ExoGoldenTestExpr(fileName: String): Mode = ExoGoldenTest(fileName)
    @DslExt
    fun ExoGoldenOverride(): Mode = ExoGoldenOverride(errorCap("No file name provided. This should be overridden by the compiler-plugin to ExoGoldenOverrideExpr"))
    fun ExoGoldenOverrideExpr(fileName: String): Mode = ExoGoldenOverride(fileName)
  }
}

abstract class GoldenSpecDynamic(val goldenQueries: GoldenQueryFile, val mode: Mode, body: io.exoquery.GoldenSpecDynamic.() -> Unit): DslDrivenSpec(), FreeSpecRootScope {

  val outputQueries = mutableListOf<PrintableValue>()

  fun TestScope.testPath() = run {
    tailrec fun rec(case: TestCase, acc: List<String>): String =
      case.parent?.let { rec(it, acc + it.name.originalName) } ?: acc.reversed().joinToString("/")
    rec(this.testCase, listOf(testCase.name.originalName))
  }

  fun TestScope.shouldBeGolden(xr: XR, suffix: String = "") = xr.shouldBeGolden(testPath() + if (suffix.isEmpty()) "" else "/$suffix")
  fun TestScope.shouldBeGolden(sql: SqlCompiledQuery<*>, suffix: String = "") = sql.value.shouldBeGolden(testPath() + if (suffix.isEmpty()) "" else "/$suffix", PrintableValue.Type.SqlQuery)

  // TODO the show() function should use the mirror idiom once that is complete
  fun XR.shouldBeGolden(label: String) = this.show().shouldBeGolden(label, PrintableValue.Type.KotlinCode)
  fun SqlCompiledQuery<*>.shouldBeGolden() = this.value.shouldBeGolden(this.label ?: errorCap("""The following query did not have a label: "$value""""), PrintableValue.Type.SqlQuery)

  fun String.shouldBeGolden(label: String, printType: PrintableValue.Type) =
    when (mode) {
      is Mode.ExoGoldenTest ->
        goldenQueries.queries[label]?.let {
          assertEquals(
            it.trimIndent().trim(), this.trimIndent().trim(),
            "Golden query for label: \"$label\" did not match"
          )
        } ?: errorCap("""No golden query found for label: "$label"""")

      is Mode.ExoGoldenOverride ->
        outputQueries.add(PrintableValue(this, printType, label))
    }

  fun complete() {
    fun isNotSep(c: Char) = c != '/' && c != '\\'
    val override = mode is Mode.ExoGoldenOverride
    val fileNameBase = mode.fileName.dropLastWhile {it != '.'}.dropLast(1).takeLastWhile { isNotSep(it) }
    val fileName = fileNameBase + "GoldenDynamic"
    val fileContent = QueryFileKotlinMaker.invoke(outputQueries, fileName, "io.exoquery")
    println("========================= Generated Override File: ${mode.fileName} =========================\n" + fileContent)
    writeToFile(mode.fileName, fileName, fileContent, override)
  }

  init {
    body()
    afterSpec { complete() }
  }
}


// This mimics FreeSpec but adds a shouldBeGolden function that compares the compiled query to a golden query
// whenever I try to extend FreeSpec I get the following error:
// kotlin.native.internal.IrLinkageError: Constructor 'GoldenSpec.<init>' can not be called: Can not instantiate abstract class 'GoldenSpec'
abstract class GoldenSpec(val goldenQueries: GoldenQueryFile, body: GoldenSpec.() -> Unit): DslDrivenSpec(), FreeSpecRootScope {
  // TODO put the label directly in the SqlCompiledQuery so we can just compare the queries directly
  // TODO make better error message output (see the shouldBeEqual for strings to see how to do this)

  // TODO modify compiler add something to golden file so intellij will identify it as such perhaps Sql(...) prefix or something like that,
  //      see how I did this in terpal-sql (and the issue they resolved). Maybe I can just add '.sql' after the query
  // TODO add support for multiline queries, they should print out as """... bunch of lines...""" and should be compared as such
  fun SqlCompiledQuery<*>.shouldBeGolden() =
    goldenQueries.queries[label]?.let {
      assertEquals(
        it.trimIndent().trim(), this.value.trimIndent().trim(),
        "Golden query for label: \"$label\" did not match"
      )
    } ?: errorCap("""No golden query found for label: "$label"""")

  init {
    body()
  }
}
