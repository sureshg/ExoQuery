package io.exoquery

import io.exoquery.xr.XR
import io.kotest.core.spec.DslDrivenSpec
import io.kotest.core.spec.style.scopes.FreeSpecRootScope
import kotlin.test.assertEquals

sealed interface Mode {
  object ExoGoldenTest : Mode
  object ExoGoldenOverride: Mode
}

abstract class GoldenSpecDynamic(val goldenQueries: GoldenQueryFile, val mode: Mode, body: io.exoquery.GoldenSpecDynamic.() -> Unit): DslDrivenSpec(), FreeSpecRootScope {

  // TODO the show() function should use the mirror idiom once that is complete
  fun XR.shouldBeGolden(label: String) = this.show().shouldBeGolden(label)
  fun SqlCompiledQuery<*>.shouldBeGolden() = this.value.shouldBeGolden(this.label ?: error("""The following query did not have a label: "$value""""))

  fun String.shouldBeGolden(label: String) =
    when (mode) {
      is Mode.ExoGoldenTest ->
        goldenQueries.queries[label]?.let {
          assertEquals(
            it.trimIndent().trim(), this.trimIndent().trim(),
            "Golden query for label: \"$label\" did not match"
          )
        } ?: error("""No golden query found for label: "$label"""")

      // TODO collect into a data structure & print the contents of the GoldenQueryFile when the test is complete
      //      use the writer in the ExoQuery compiler plugin here to do the same (might want to move that writer to the Runtime module)
      is Mode.ExoGoldenOverride ->
        TODO()
    }

  init {
    body()
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
    } ?: error("""No golden query found for label: "$label"""")

  init {
    body()
  }
}
