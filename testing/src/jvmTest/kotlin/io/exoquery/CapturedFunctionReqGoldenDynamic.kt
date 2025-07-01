package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object CapturedFunctionReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "limited container/using limited container/XR" to kt(
      "select { val p = from(Table(Person)); p }"
    ),
    "limited container/using limited container/SQL" to cr(
      "SELECT x.id, x.name, x.age FROM Person x"
    ),
    "limited container/using limited container/Phase" to cr(
      "CompileTime"
    ),
    "limited container/using limited container with captured function/XR" to kt(
      "select { val p = from({ name -> Table(Person).filter { p -> p.name == name } }.toQuery.apply(Joe)); p }"
    ),
    "limited container/using limited container with captured function/SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe'"
    ),
    "limited container/using limited container with captured function/Phase" to cr(
      "CompileTime"
    ),
  )
}
