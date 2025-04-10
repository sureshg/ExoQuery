package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object CapturedFunctionDynamicReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "dynamic catpured function with join clauses/XR" to kt(
      """Table(Person).filter { p -> TagE("cea6d") }"""
    ),
    "dynamic catpured function with join clauses/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = {0:Joe} OR p.name = {1:Jack} OR p.name = {2:Jill}",
      "f6a5733d-731b-45ad-88b5-f38869ca0e65" to "Joe", "1801e6dd-d60b-4db5-80ec-79432514818c" to "Jack", "8d379fe3-446e-45ff-895f-d8ce4f799227" to "Jill"
    ),
  )
}
