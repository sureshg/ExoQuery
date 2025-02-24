package io.exoquery

import io.exoquery.printing.cr
import io.exoquery.printing.kt

object ParamReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, String>(
    "single single-param/compileTime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = <UNR?>"
    ),
    "single single-param/compileTime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ?"
    ),
    "single single-param/compileTime/Params" to cr(
      "[ParamSingle(id=BID(value=0), value=Leah, serial=String)]"
    ),
    "single single-param/runtime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ?"
    ),
    "single single-param/runtime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ?"
    ),
    "single single-param/runtime/Params" to cr(
      "[ParamSingle(id=BID(value=0), value=Leah, serial=String)]"
    ),
    "multi single-param/compileTime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = <UNR?> AND p.age = <UNR?>"
    ),
    "multi single-param/compileTime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ? AND p.age = ?"
    ),
    "multi single-param/compileTime/Params" to cr(
      "[ParamSingle(id=BID(value=0), value=Leib, serial=String), ParamSingle(id=BID(value=1), value=42, serial=Int)]"
    ),
    "multi single-param/runtime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ? AND p.age = ?"
    ),
    "multi single-param/runtime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ? AND p.age = ?"
    ),
    "multi single-param/runtime/Params" to cr(
      "[ParamSingle(id=BID(value=0), value=Leib, serial=String), ParamSingle(id=BID(value=1), value=42, serial=Int)]"
    ),
    "single multi-param/compileTime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name IN (<UNRS?>)"
    ),
    "single multi-param/compileTime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name IN (?, ?)"
    ),
    "single multi-param/compileTime/Params" to cr(
      "[ParamMulti(id=BID(value=0), value=[Leah, Leib], serial=String)]"
    ),
    "single multi-param/runtime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name IN (?, ?)"
    ),
    "single multi-param/runtime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE p.name IN (?, ?)"
    ),
    "single multi-param/runtime/Params" to cr(
      "[ParamMulti(id=BID(value=0), value=[Leah, Leib], serial=String)]"
    ),
    "multi multi-param/compileTime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name IN (<UNRS?>) THEN p.age IN (<UNRS?>) ELSE false END"
    ),
    "multi multi-param/compileTime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name IN (?, ?) THEN p.age IN (?, ?) ELSE false END"
    ),
    "multi multi-param/compileTime/Params" to cr(
      "[ParamMulti(id=BID(value=0), value=[Leah, Leib], serial=String), ParamMulti(id=BID(value=1), value=[42, 43], serial=Int)]"
    ),
    "multi multi-param/runtime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name IN (?, ?) THEN p.age IN (?, ?) ELSE false END"
    ),
    "multi multi-param/runtime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name IN (?, ?) THEN p.age IN (?, ?) ELSE false END"
    ),
    "multi multi-param/runtime/Params" to cr(
      "[ParamMulti(id=BID(value=0), value=[Leah, Leib], serial=String), ParamMulti(id=BID(value=1), value=[42, 43], serial=Int)]"
    ),
    "one single, one multi/compileTime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name = <UNR?> THEN p.age IN (<UNRS?>) ELSE false END"
    ),
    "one single, one multi/compileTime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name = ? THEN p.age IN (?, ?) ELSE false END"
    ),
    "one single, one multi/compileTime/Params" to cr(
      "[ParamSingle(id=BID(value=0), value=Joe, serial=String), ParamMulti(id=BID(value=1), value=[42, 43], serial=Int)]"
    ),
    "one single, one multi/runtime/Original SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name = ? THEN p.age IN (?, ?) ELSE false END"
    ),
    "one single, one multi/runtime/Determinized SQL" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE CASE WHEN p.name = ? THEN p.age IN (?, ?) ELSE false END"
    ),
    "one single, one multi/runtime/Params" to cr(
      "[ParamSingle(id=BID(value=0), value=Joe, serial=String), ParamMulti(id=BID(value=1), value=[42, 43], serial=Int)]"
    ),
  )
}
