package io.exoquery

import io.exoquery.sql.SqlIdiom
import io.exoquery.util.TraceConfig

class PostgresDialect(override val traceConfig: TraceConfig) : SqlIdiom {
  override val concatFunction: String = "||"
  override val useActionTableAliasAs = SqlIdiom.ActionTableAliasBehavior.UseAs
}
