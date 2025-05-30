package io.exoquery.printing


object QueryFileKotlinMakerRoom {
  private fun String.indentBy(spaces: Int) = this.lines().joinToString("\n") { " ".repeat(spaces) + it }
  private fun String.commentOut() = this.lines().joinToString("\n") { "// ${it}" }
  private fun String.isMultiline() = this.contains('\n') || this.contains('\r')


  private fun singleLineQuery(label: String, query: String, printable: PrintableValue) = run {
    // create every line of the GoldeQueryFile
    val type = printable.type

    val qqq = """"""".repeat(3)
    val row = // TODO if it's `val x = capture.select { ... }` then `x` should be the default label
      if (query.contains('"')) {
        """|  @Query(${qqq}${query}${qqq})
           |  fun ${label}(): ${printable.queryOutputType}
        """.trimMargin()
      } else {
        """|  @Query("${query}")
           |  fun ${label}(): ${printable.queryOutputType}
        """.trimMargin()
      }

    row
  }

  private fun multiLineQuery(label: String, query: String, printable: PrintableValue) = run {
    val type = printable.type

    val qqq = """"""".repeat(3)
    val row =
      """|  @Query(
         |    ${qqq}
         |${query.indentBy(4)}
         |    ${qqq}
         |  )
         |  fun ${label}(): ${printable.queryOutputType}
      """.trimMargin()
    row
  }

  operator fun invoke(queries: List<PrintableValue>, fileName: String, filePackage: String): String {

    val dol = '$'
    fun String.escapeDollar() = this.replace("$", "$dol{'$dol'}")

    val fileBody =
      queries
        .map { qf ->
          if (qf.label == null) {
            // TODO when printable-value includes locaiton string add here
            ("// Query without label:\n" + qf.value.commentOut()).indentBy(2) + "\n"
          } else if (qf.value.isMultiline())
            multiLineQuery(qf.label, qf.value.escapeDollar(), qf)
          else
            singleLineQuery(qf.label, qf.value.escapeDollar(), qf)
        }.joinToString("\n")

    // Need to have 'mapOf<String, String>' not just mapOf because otherwise when it is empty the type won't be inferred leading to a compile error
    return (
        """|package $filePackage
         |
         |import androidx.room.Dao
         |import androidx.room.Query
         |
         |interface ${fileName} {
         |$fileBody
         |}
         |""".trimMargin()
        )
  }
}
