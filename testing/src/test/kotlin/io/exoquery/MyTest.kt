package io.exoquery

import kotlin.reflect.typeOf

fun main(args: Array<String>) {
  val cap = capture { 123 }
  //println(readAnnotations(cap))

  //showAnnotations(capture { 123 }) //hello hello hello
}

//inline fun <reified T> readAnnotations(value: T) =
//  typeOf<T>().annotations
