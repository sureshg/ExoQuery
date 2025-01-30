package io.exoquery.plugin.transform

object QueryFileKotlinMaker {
  private fun String.indentBy(spaces: Int) = this.lines().joinToString("\n") { " ".repeat(spaces) + it }
  private fun String.isMultiline() = this.contains('\n') || this.contains('\r')

  private fun singleLineQuery(label: String, query: String) = run {
      // create every line of the GoldeQueryFile
      val row =
        """|    "${label}" to cr(
           |      "${query}"
           |    ),
        """.trimMargin()
      row
  }
  private fun multiLineQuery(label: String, query: String) = run {
    // create every line of the GoldeQueryFile
    val qqq = """"""".repeat(3)
    val row =
      """|    "${label}" to cr(
         |      ${qqq}
         |${query.indentBy(6)}
         |      ${qqq}
         |    ),
      """.trimMargin()
    row
  }

  operator fun invoke(queries: List<PrintableQuery>, fileName: String, filePackage: String): String {


    val fileBody =
      queries
        .filter { qf ->
          qf.label != null
        }
        .mapNotNull { qf ->
          val label = qf.label!!
          if (qf.query.isMultiline())
            multiLineQuery(label, qf.query)
          else
            singleLineQuery(label, qf.query)
        }.joinToString("\n")

    return (
      """|package $filePackage
         |
         |import io.exoquery.printing.cr
         |
         |object ${fileName}Golden: GoldenQueryFile {
         |  override val queries = mapOf(
         |$fileBody
         |  )
         |}
         |""".trimMargin()
      )
  }
}
