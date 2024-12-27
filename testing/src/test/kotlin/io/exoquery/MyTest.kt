package io.exoquery

import io.exoquery.printing.PrintXR
import io.exoquery.printing.qprint
import kotlin.reflect.typeOf

fun main(args: Array<String>) {
  val cap = capture { 123 }
  println(qprint(cap.xr))

  //println(readAnnotations(cap))

  //showAnnotations(capture { 123 }) //hello hello hello
}

//inline fun <reified T> readAnnotations(value: T) =
//  typeOf<T>().annotations
