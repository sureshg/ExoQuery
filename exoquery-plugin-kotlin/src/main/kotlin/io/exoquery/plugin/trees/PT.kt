package io.exoquery.plugin.trees

// Common Dsl Paths used for extracting/calling Ir. Want to keep the name of this brief in order to minimize syntactic noise
object PT {
  val io_exoquery_util_scaffoldCapFunctionQuery = "io.exoquery.util.scaffoldCapFunctionQuery"
  val io_exoquery_unpackQuery = "io.exoquery.unpackQuery"
  val io_exoquery_unpackExpr = "io.exoquery.unpackExpr"
  val io_exoquery_SqlQuery = "io.exoquery.SqlQuery"
  val io_exoquery_SqlExpression = "io.exoquery.SqlExpression"
  val io_exoquery_Table = "io.exoquery.Table"
  // this matches the function-call Runtimes.Empty which is needed for an SqlQuery/SqlExpression to be uprootable
  val EmptyRuntimes = "Empty"
}
