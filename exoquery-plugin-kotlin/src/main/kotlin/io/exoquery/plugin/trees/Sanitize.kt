package io.exoquery.plugin.trees

import io.exoquery.xr.XR
import org.jetbrains.kotlin.name.FqName

fun FqName.sanitizedClassName(): String =
  this.pathSegments().filterNot { it.toString() == "<init>" || it.toString() == "<anonymous>" }.last().asString()

fun String.sanitizeIdentName() = this.replace("<", "").replace(">", "")
