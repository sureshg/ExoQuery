package io.exoquery.sql

import io.exoquery.util.TraceConfig
import io.exoquery.util.Tracer
import io.exoquery.util.unaryPlus
import io.exoquery.xr.XR

class SqliteDialect(override val traceConf: TraceConfig = TraceConfig.empty) : SqlIdiom, BooleanLiteralSupport {
  override val concatFunction: String = "||"
  override val useActionTableAliasAs = SqlIdiom.ActionTableAliasBehavior.UseAs
  override val trace: Tracer by lazy { Tracer(traceType, traceConf, 1) }

  override fun xrOrderByCriteriaTokenImpl(orderByCriteriaImpl: OrderByCriteria): Token = with (orderByCriteriaImpl) {
    when (this.ordering) {
      is XR.Ordering.Asc -> +"${scopedTokenizer(ast)} ASC"
      is XR.Ordering.Desc -> +"${scopedTokenizer(ast)} DESC"
      is XR.Ordering.AscNullsFirst -> +"${scopedTokenizer(ast)} ASC /* NULLS FIRST */"
      is XR.Ordering.DescNullsFirst -> +"${scopedTokenizer(ast)} DESC /* NULLS FIRST */"
      is XR.Ordering.AscNullsLast -> +"${scopedTokenizer(ast)} ASC /* NULLS LAST */"
      is XR.Ordering.DescNullsLast -> +"${scopedTokenizer(ast)} DESC /* NULLS LAST */"
    }
  }
}

//
//  private val _emptySetContainsToken = StringToken("0")
//
//  override def emptySetContainsToken(field: Token): Token = _emptySetContainsToken
//
//  override def prepareForProbing(string: String): String = s"sqlite3_prepare_v2($string)"
//
//  override def astTokenizer(implicit
//    astTokenizer: Tokenizer[Ast],
//    strategy: NamingStrategy,
//    idiomContext: IdiomContext
//  ): Tokenizer[Ast] =
//  Tokenizer[Ast] {
//    case c: OnConflict => conflictTokenizer.token(c)
//    case ast           => super.astTokenizer.token(ast)
//  }
//
//  private[this] val omittedNullsOrdering = stmt"omitted (not supported by sqlite)"
//  private[this] val omittedNullsFirst    = stmt"/* NULLS FIRST $omittedNullsOrdering */"
//  private[this] val omittedNullsLast     = stmt"/* NULLS LAST $omittedNullsOrdering */"
//
//  override implicit def orderByCriteriaTokenizer(implicit
//    astTokenizer: Tokenizer[Ast],
//  strategy: NamingStrategy
//  ): Tokenizer[OrderByCriteria] = Tokenizer[OrderByCriteria] {
//    case OrderByCriteria(ast, Asc) =>
//    stmt"${scopedTokenizer(ast)} ASC"
//    case OrderByCriteria(ast, Desc) =>
//    stmt"${scopedTokenizer(ast)} DESC"
//    case OrderByCriteria(ast, AscNullsFirst) =>
//    stmt"${scopedTokenizer(ast)} ASC $omittedNullsFirst"
//    case OrderByCriteria(ast, DescNullsFirst) =>
//    stmt"${scopedTokenizer(ast)} DESC $omittedNullsFirst"
//    case OrderByCriteria(ast, AscNullsLast) =>
//    stmt"${scopedTokenizer(ast)} ASC $omittedNullsLast"
//    case OrderByCriteria(ast, DescNullsLast) =>
//    stmt"${scopedTokenizer(ast)} DESC $omittedNullsLast"
//  }
//
