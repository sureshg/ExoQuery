package io.exoquery.util

import com.github.vertical_blank.sqlformatter.SqlFormatter

actual fun formatQuery(query: String): String =
  SqlFormatter.format(query)
