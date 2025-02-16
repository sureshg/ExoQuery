package io.exoquery.xr

import io.exoquery.ContainerOfXR
import io.exoquery.SqlCompiledQuery
import io.exoquery.SqlQuery
import io.exoquery.sql.SqlIdiom
import io.exoquery.util.formatQuery

class RuntimeQueryBuilder<T>(val query: SqlQuery<T>, val dialect: SqlIdiom, val label: String?, val pretty: Boolean) {
  operator fun invoke(): SqlCompiledQuery<T> {
    // First thing we need to do is dedupe any runtime binds. We need to do this while the whole AST is still hierarchically
    // organized i.e. before flattening. This happens because in dynamic situations that collect and then combine multiple queries e.g:
    // List("a", "b").map(a => quote { query[Person].filter(p => p.name == lift(a)) }).reduce(_ ++ _)
    // since the lift(a) UID is determined at compile-time the trees of both "a", and "b" variants will have the same value for it
    // resulting in a tree that looks like this (before being flattened into a single filter which happens during query compilation):
    // UnionAll(
    //   Filter(Entity("Person"), BinaryOperation(p.name, ==, ScalarTag("SOME_UUID"))
    //   Filter(Entity("Person"), BinaryOperation(p.name, ==, ScalarTag("SOME_UUID"))
    // )
    // This will result in incorrect queries. Therefore we need to dedupe the runtime the "SOME_UUID" binds here.
    // TODO need test with the above case
    val quoted = query.rekeyRuntimeBinds()
    val splicedAst = spliceQuotations(quoted) as XR.Query

    val queryRaw = dialect.translate(splicedAst)
    val query = if (pretty) formatQuery(queryRaw) else queryRaw
    return SqlCompiledQuery(query, label)
  }

  // TODO need a test with a dynamic SqlExpression container used in an SqlQuery (and vice-versa)
  // TODO will need to gather the Params from all of the nested query containers when lifting system is introduced

  fun spliceQuotations(quoted: SqlQuery<*>): XR.Labels.QueryOrExpression {
    fun spliceQuotationsRecurse(quoted: ContainerOfXR): XR {
      val quotationVases = quoted.runtimes.runtimes
      val ast = quoted.xr
      // Get all the quotation tags
      return TransformXR.build()
        .withQueryOf<XR.TagForSqlQuery> { tag ->
          quotationVases.find { it.first == tag.id }?.let { (id, vase) -> spliceQuotationsRecurse(vase) as XR.Query }
            ?: throw IllegalArgumentException("Query-Based vase with UID ${tag.id} could not be found!")
        }.withExpressionOf<XR.TagForSqlExpression> { tag ->
          quotationVases.find { it.first == tag.id }?.let { (id, vase) -> spliceQuotationsRecurse(vase) as XR.Expression }
            ?: throw IllegalArgumentException("Expression-Based vase with UID ${tag.id} could not be found!")
        }.invoke(ast)
    }
    return BetaReduction(spliceQuotationsRecurse(quoted) as XR.Labels.QueryOrExpression)
  }

// Kotlin:
//  def spliceQuotations(quoted: Quoted[_]): Ast = {
//    def spliceQuotationsRecurse(quoted: Quoted[_]): Ast = {
//      val quotationVases = quoted.runtimeQuotes
//      val ast = quoted.ast
//      // Get all the quotation tags
//      Transform(ast) {
//        // Splice the corresponding vase for every tag, then recurse
//        case v @ QuotationTag(uid) =>
//        // When a quotation to splice has been found, retrieve it and continue
//        // splicing inside since there could be nested sections that need to be spliced
//        quotationVases.find(_.uid == uid) match {
//          case Some(vase) => spliceQuotationsRecurse(vase.quoted)
//          case None => throw new IllegalArgumentException(s"Quotation vase with UID ${uid} could not be found!")
//        }
//      }
//    }
//    BetaReduction(spliceQuotationsRecurse(quoted))
//  }
}
