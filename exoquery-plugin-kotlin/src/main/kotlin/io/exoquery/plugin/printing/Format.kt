package io.exoquery.plugin.printing

// Currently not used ahywhere. If we really need source printing can bring this back. Need to add dependencies to GradlePlugin
// Note that I don't really like ktfmt because it's a bit slow
//import com.facebook.ktfmt.format.Formatter
//
//fun format(code: String): String {
//  val format =
//    Formatter.GOOGLE_FORMAT.copy(
//      maxWidth = 100, blockIndent = 2
//    )
//  val codeString = code.toString()
//  val output =
//    try {
//      Formatter.format(format, "fun stuff() { ${codeString} }")
//        .replaceFirst("fun stuff() {", "").dropLastWhile { it == '}' || it.isWhitespace() }
//    } catch (e: Exception) {
//      codeString
//    }
//
//  return output
//}
