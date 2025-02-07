package io.exoquery.util

actual fun getExoVariable(propName: String, envName: String, default: String): String =
  System.getProperty(propName) ?: System.getenv(envName) ?: default
