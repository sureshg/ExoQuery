package io.exoquery.util

import platform.posix.*
import kotlinx.cinterop.*


// KMP uses kotlinx.cinterop.* from kotlin-stdlib-common. See https://stackoverflow.com/a/55002326
@OptIn(ExperimentalForeignApi::class)
actual fun getExoVariable(propName: String, envName: String, default: String): String =
  getenv(envName)?.toKString() ?: default
