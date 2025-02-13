package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object MirrorContextSpecGolden: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "XR.Expression/XR.Ident" to kt(
      "foo"
    ),
    "XR.Expression/XR.Const.Int" to kt(
      "42"
    ),
    "XR.Expression/XR.Property" to kt(
      "foo.bar"
    ),
    "XR.Expression/XR.Property Nested" to kt(
      "foo.bar.baz"
    ),
    "XR.Expression/XR.When 1-Branch" to kt(
      "if (foo) bar else baz"
    ),
    "XR.Expression/XR.When 2-Branch" to kt(
      "when { foo -> bar; baz -> qux; else -> quux }"
    ),
  )
}
