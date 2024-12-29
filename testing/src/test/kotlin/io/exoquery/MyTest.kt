package io.exoquery

import io.exoquery.printing.PrintXR
import io.exoquery.printing.qprint
import kotlin.reflect.typeOf

fun case0() {
  val cap0 = capture { 456 }
  val cap1 = cap0
  val cap = capture { 123 + cap1.use }
  println(qprint(cap.xr))
}

//fun case1() {
//  //val cap = capture { 123 + capture { 456 }.use }
//  //println(qprint(cap.xr))
//
//  class Foo {
//    val cap0 = capture { 456 }
//  }
//  val f = Foo()
//  val cap = capture { 123 + f.cap0.use }
//  println(qprint(cap.xr))
//}

fun main() {
  case0()
  //case1()
}

//inline fun <reified T> readAnnotations(value: T) =
//  typeOf<T>().annotations
