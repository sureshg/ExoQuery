package io.exoquery

import io.exoquery.sql.BooleanLiteralSupport
import io.exoquery.sql.SqlIdiom
import io.exoquery.util.TraceConfig
import io.exoquery.util.Tracer

class BooleanLiteralTestDialect(override val traceConf: TraceConfig = TraceConfig.empty) : BooleanLiteralSupport, SqlIdiom {
  override val concatFunction: String = "||"
  override val useActionTableAliasAs = SqlIdiom.ActionTableAliasBehavior.UseAs

  override val trace: Tracer by lazy { Tracer(traceType, traceConf, 1) }
}
