package io.exoquery

interface GoldenQueryFile {
  val queries: Map<String, String> get() = emptyMap()
  companion object {
    val Empty = object : GoldenQueryFile {}
  }
}



object Sample: GoldenQueryFile {
  override val queries = mapOf(
    "query1" to "SELECT * FROM table1",
    "query2" to "SELECT * FROM table2",
    "query3" to
      """
      SELECT * FROM table3
      WHERE column1 = 'value1'  
      """.trimMargin()
  )
}