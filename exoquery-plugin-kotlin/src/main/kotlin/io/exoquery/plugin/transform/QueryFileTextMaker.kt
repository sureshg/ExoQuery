package io.exoquery.plugin.transform

import io.exoquery.plugin.show

object QueryFileTextMaker {
  val LabelPrefix: String = "-- ===========< "
  val LabelSuffix: String = " >========== --"
  val PathPrefix: String = "-- Path: "

  operator fun invoke(queries: List<PrintableQuery>, pathBehavior: QueryAccumState.PathBehavior, labelBehavior: QueryAccumState.LabelBehavior): String =
    queries
      .mapNotNull { query ->
        when (labelBehavior) {
          is QueryAccumState.LabelBehavior.IncludeOnlyLabeled -> if (query.label != null) query else null
          is QueryAccumState.LabelBehavior.IncludeAll -> query
        }
      }
      .mapNotNull { query ->
        val labelLine = query.label?.let { "${LabelPrefix}$it${LabelSuffix}" } ?: ""
        val pathLine = "${PathPrefix}${query.location.show()}"
        when (pathBehavior) {
          is QueryAccumState.PathBehavior.IncludePaths ->
            // first the label prefix, label, label suffix, then the path, then the query
            """|
               |$labelLine
               |$pathLine
               |${query.query}
               |
            """.trimMargin()
          is QueryAccumState.PathBehavior.NoIncludePaths ->
            // first the label prefix, label, label suffix, then the query
            """|
               |$labelLine
               |${query.query}
               |
            """.trimMargin()
        }
      }.joinToString("\n")
}
