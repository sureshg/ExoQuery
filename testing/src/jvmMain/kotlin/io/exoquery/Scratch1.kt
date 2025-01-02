package io.exoquery

import io.exoquery.printing.PrintXR
import io.exoquery.printing.qprint
import kotlin.reflect.typeOf

fun main() {
  // test test test test test
  case0()
}

data class Foo(val bar: String)

fun case0() {
  val cap0 = capture { 123 + param(456) }
  println(cap0.params)

  val cap1 = cap0
  val cap = capture { 789 + cap1.use }
  println(qprint(cap.xr))
  println(cap.params)

  /*
  java.lang.IllegalStateException: ------- Calling replant -------
CALL 'public final fun <get-params> (): io.exoquery.Params declared in io.exoquery.SqlExpression' type=io.exoquery.Params origin=GET_PROPERTY
  $this: GET_VAR 'val cap0: @[Captured(value = "initial-value")] io.exoquery.SqlExpression<kotlin.Int> [val] declared in io.exoquery.case0' type=@[Captured(value = "initial-value")] io.exoquery.SqlExpression<kotlin.Int> origin=null

  ================= IR: ========================
RETURN type=kotlin.Nothing from='local final fun <anonymous> (): kotlin.String declared in io.exoquery.case0'
  CALL 'public final fun <get-bar> (): kotlin.String declared in io.exoquery.Foo' type=kotlin.String origin=GET_PROPERTY
    $this: GET_VAR 'val f: io.exoquery.Foo [val] declared in io.exoquery.case0' type=io.exoquery.Foo origin=null

   */
  //val f = Foo("hello")
  //printSource { f.bar }
}