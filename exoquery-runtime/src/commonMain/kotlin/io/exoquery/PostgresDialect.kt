package io.exoquery

import io.exoquery.sql.SqlIdiom
import io.exoquery.util.TraceConfig
import io.exoquery.util.Tracer

class PostgresDialect(override val traceConf: TraceConfig = TraceConfig.empty) : SqlIdiom {
  override val useActionTableAliasAs = SqlIdiom.ActionTableAliasBehavior.UseAs

  override val trace: Tracer by lazy { Tracer(traceType, traceConf, 1) }
}
