package io.exoquery.util

import io.exoquery.SqlQuery

fun <T> scaffoldCapFunctionQuery(query: T, vararg values: Any?): T =
  // The purpose of this function is to carry around the arguments of a compile-time transformed @CapturedFunction instance.
  // It should not be called anywhere in the runtime.
  error("A @CapturedFunction call cannot be used outside of a capture block")
