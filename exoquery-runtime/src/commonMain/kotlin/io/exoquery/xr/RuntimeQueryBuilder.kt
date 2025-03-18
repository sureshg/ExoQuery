package io.exoquery.xr

import io.exoquery.ContainerOfActionXR
import io.exoquery.ContainerOfFunXR
import io.exoquery.ContainerOfXR
import io.exoquery.ParamSet
import io.exoquery.Phase
import io.exoquery.SqlAction
import io.exoquery.SqlBatchAction
import io.exoquery.SqlQuery
import io.exoquery.sql.ParamMultiToken
import io.exoquery.sql.ParamSingleToken
import io.exoquery.sql.SqlIdiom
import io.exoquery.sql.SqlQueryModel
import io.exoquery.sql.StatelessTokenTransformer
import io.exoquery.sql.Token
import io.exoquery.util.formatQuery

class RuntimeBuilder(val dialect: SqlIdiom, val pretty: Boolean) {
  data class ContainerBuildQuery(val queryString: String, val queryTokenized: Token, val queryModel: SqlQueryModel)
  data class ContainerBuildAction(val queryString: String, val queryTokenized: Token)


  private fun <Other> processContainer(container: ContainerOfXR, tokenize: (XR) -> Pair<Token, Other>): Triple<String, Token, Other> {
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
    val quoted = container.rekeyRuntimeBinds()
    val splicedAst = spliceQuotations(quoted)

    val (queryTokenizedRaw, other) = tokenize(splicedAst)

    // "realize" the tokens converting params clauses to actual list-values
    val queryTokenized = TokenRealizer(container.params).invoke(queryTokenizedRaw)

    val queryRaw = queryTokenized.build()
    val queryString = if (pretty) formatQuery(queryRaw) else queryRaw
    return Triple(queryString, queryTokenized, other)
  }

  fun forQuery(container: ContainerOfFunXR): ContainerBuildQuery {
    val (queryString, queryToken, queryModel) =
      processContainer(container) { splicedAst ->
        when (splicedAst) {
          // If it is an expression attempt to convert into a query-type and run it. There are situations in which toExpr is introduced around a query etc... and we can handle that in SqlQuery processing
          is XR.Expression -> dialect.processQuery(splicedAst.asQuery())
          is XR.Query -> dialect.processQuery(splicedAst)
          else -> throw IllegalArgumentException("Unsupported XR type. Can only be a XR.Query: ${splicedAst::class}\n${splicedAst.showRaw()}")
        }
      }

    return ContainerBuildQuery(queryString, queryToken, queryModel)
  }

  fun forAction(container: SqlAction<*, *>): ContainerBuildAction {
    val (queryString, queryToken, _) =
      processContainer(container) { splicedAst ->
        when (splicedAst) {
          is XR.Action -> dialect.processAction(splicedAst) to null
          else -> throw IllegalArgumentException("Unsupported XR type. Can only be a XR.Action: ${splicedAst::class}\n${splicedAst.showRaw()}")
        }
      }

    return ContainerBuildAction(queryString, queryToken)
  }

  fun forBatching(container: SqlBatchAction<*, *>): ContainerBuildAction {
    val (queryString, queryToken, _) =
      processContainer(container) { splicedAst ->
        when (splicedAst) {
          is XR.Batching -> dialect.processBatching(splicedAst) to null
          else -> throw IllegalArgumentException("Unsupported XR type. Can only be a XR.Batching: ${splicedAst::class}\n${splicedAst.showRaw()}")
        }
      }

    return ContainerBuildAction(queryString, queryToken)
  }

  class TokenRealizer(val paramSet: ParamSet): StatelessTokenTransformer {
    override fun invoke(token: ParamMultiToken): Token = token.realize(paramSet)
    override fun invoke(token: ParamSingleToken): Token = token.realize(paramSet)
  }

  // TODO need a test with a dynamic SqlExpression container used in an SqlQuery (and vice-versa)
  // TODO will need to gather the Params from all of the nested query containers when lifting system is introduced

  fun spliceQuotations(quoted: ContainerOfXR): XR {
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
    return BetaReduction.ofXR(spliceQuotationsRecurse(quoted))
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
