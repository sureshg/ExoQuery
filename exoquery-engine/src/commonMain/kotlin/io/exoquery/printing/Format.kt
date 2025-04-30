//package io.exoquery.printing
//
//import com.facebook.ktfmt.format.Formatter
//import com.facebook.ktfmt.format.ParseError
//
//fun format(code: String): String {
//  val format =
//    Formatter.GOOGLE_FORMAT.copy(
//      maxWidth = 100, blockIndent = 2
//    )
//  val codeToFormat = /*"fun stuff() =*/ "${code.toString()}".replace("$", "")
//  return try {
//    Formatter.format(format, codeToFormat)
//  } catch (e: ParseError) {
//    throw IllegalArgumentException(
//      "\n" +
//      """|------------ Failed to format: ------------
//         |${codeToFormat}
//         |---------------------
//         |${e.message}
//         |""".trimMargin()
//    )
//  }
//}
// TODO need to move this to the plugin module since the google formatting library is not KMP