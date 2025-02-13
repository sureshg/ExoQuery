package io.exoquery.printing

data class PrintableValue(val value: String, val type: Type, val label: String? = null) {
  sealed interface Type {
    val interpolatorPrefix: String
    object SqlQuery : Type { override val interpolatorPrefix = "cr" }
    object KotlinCode : Type { override val interpolatorPrefix = "kt" }
  }
}

object QueryFileKotlinMaker {
  private fun String.indentBy(spaces: Int) = this.lines().joinToString("\n") { " ".repeat(spaces) + it }
  private fun String.isMultiline() = this.contains('\n') || this.contains('\r')

  private fun singleLineQuery(label: String, query: String, type: PrintableValue.Type) = run {
      // create every line of the GoldeQueryFile
      val row =
        """|    "${label}" to ${type.interpolatorPrefix}(
           |      "${query}"
           |    ),
        """.trimMargin()
      row
  }
  private fun multiLineQuery(label: String, query: String, type: PrintableValue.Type) = run {
    // create every line of the GoldeQueryFile
    val qqq = """"""".repeat(3)
    val row =
      """|    "${label}" to ${type.interpolatorPrefix}(
         |      ${qqq}
         |${query.indentBy(6)}
         |      ${qqq}
         |    ),
      """.trimMargin()
    row
  }

  operator fun invoke(queries: List<PrintableValue>, fileName: String, filePackage: String): String {


    val fileBody =
      queries
        .filter { qf ->
          qf.label != null
        }
        .mapNotNull { qf ->
          val label = qf.label!!
          if (qf.value.isMultiline())
            multiLineQuery(label, qf.value, qf.type)
          else
            singleLineQuery(label, qf.value, qf.type)
        }.joinToString("\n")

    // Need to have 'mapOf<String, String>' not just mapOf because otherwise when it is empty the type won't be inferred leading to a compile error
    return (
      """|package $filePackage
         |
         |import io.exoquery.printing.cr
         |import io.exoquery.printing.kt
         |
         |object ${fileName}Golden: GoldenQueryFile {
         |  override val queries = mapOf<String, String>(
         |$fileBody
         |  )
         |}
         |""".trimMargin()
      )
  }
}
