package io.exoquery.util

actual fun getExoVariable(propName: String, envName: String, default: String): String = run {
  val value = js("window")[propName]
  if (value.isNullOrEmpty()) default
  else (value as? String) ?: default
}
