package io.exoquery.plugin.transform

class ParseException(msg: String) : Exception(msg)
class IllegalStructureException(msg: String) : Exception(msg)

fun parseFail(msg: String): Nothing = throw ParseException(msg)

fun illegalStruct(msg: String): Nothing = throw IllegalStructureException(msg)