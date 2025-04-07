package io.exoquery

import io.exoquery.sql.SqlIdiom
import io.exoquery.util.TraceConfig
import io.exoquery.util.Tracer

/**
 * H2Dialect is a dialect for H2 database.
 *
 * TODO the only major difference with the standard SqlIdiom/Postgres is on-conflict rendering,
 *      need to add then when OnConflict is implemented.
 */
class H2Dialect(override val traceConf: TraceConfig = TraceConfig.empty) : SqlIdiom {
  override val concatFunction: String = "||"
  override val useActionTableAliasAs = SqlIdiom.ActionTableAliasBehavior.UseAs

  override val trace: Tracer by lazy { Tracer(traceType, traceConf, 1) }
}
