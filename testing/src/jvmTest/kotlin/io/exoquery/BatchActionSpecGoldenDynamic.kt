package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object BatchActionSpecGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "insert/simple/SQL" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Refiner_STRING}, {1:Refiner_INT})",
      "0" to "Refiner_STRING", "1" to "Refiner_INT"
    ),
    "insert/simple/Params" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:John}, {1:42})",
      "0" to "John", "1" to "42"
    ),
    "insert/simple/Params" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Jane}, {1:43})",
      "0" to "Jane", "1" to "43"
    ),
    "insert/simple/Params" to cr(
      "INSERT INTO Person (name, age) VALUES ({0:Jack}, {1:44})",
      "0" to "Jack", "1" to "44"
    ),
    "insert/simple" to kt(
      "[ParamSingle(0, John, String), ParamSingle(1, 42, Int)], [ParamSingle(0, Jane, String), ParamSingle(1, 43, Int)], [ParamSingle(0, Jack, String), ParamSingle(1, 44, Int)]"
    ),
    "insert/simple with setParams/SQL" to cr(
      "INSERT INTO Person (id, name, age) VALUES ({0:Refiner_INT}, {1:Refiner_STRING}, {2:Refiner_INT})",
      "0" to "Refiner_INT", "1" to "Refiner_STRING", "2" to "Refiner_INT"
    ),
    "insert/simple with setParams" to kt(
      "[ParamSingle(0, 1, Int), ParamSingle(1, John, String), ParamSingle(2, 42, Int)], [ParamSingle(0, 2, Int), ParamSingle(1, Jane, String), ParamSingle(2, 43, Int)], [ParamSingle(0, 3, Int), ParamSingle(1, Jack, String), ParamSingle(2, 44, Int)]"
    ),
  )
}
