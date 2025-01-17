package io.exoquery

// Test TEst
fun main() {
  data class Content(val title: String, val body2: String) // Test Test Test Test Test
  val content = capture { Table<Content>() }
  content.build(PostgresDialect())
}