package io.exoquery.sql

import io.exoquery.sql.BooleanLiteralSupport
import io.exoquery.sql.SqlIdiom
import io.exoquery.sql.Statement
import io.exoquery.sql.Token
import io.exoquery.util.TraceConfig
import io.exoquery.util.Tracer
import io.exoquery.xr.XR
import io.exoquery.util.unaryPlus
import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.OP
import io.exoquery.xrError

class SqlServerDialect(override val traceConf: TraceConfig = TraceConfig.empty) : SqlIdiom, BooleanLiteralSupport {
  override val concatFunction: String = "+"
  override val useActionTableAliasAs = SqlIdiom.ActionTableAliasBehavior.UseAs
  override val trace: Tracer by lazy { Tracer(traceType, traceConf, 1) }
  override val reservedKeywords: Set<String> = setOf("add", "all", "alter", "and", "any", "as", "asc", "authorization", "backup", "begin", "between", "break", "browse", "bulk", "by", "cascade", "case", "check", "checkpoint", "close", "clustered", "coalesce", "collate", "column", "commit", "compute", "constraint", "contains", "containstable", "continue", "convert", "create", "cross", "current", "current_date", "current_time", "current_timestamp", "current_user", "cursor", "database", "dbcc", "deallocate", "declare", "default", "delete", "deny", "desc", "disk", "distinct", "distributed", "double", "drop", "dump", "else", "end", "errlvl", "escape", "except", "exec", "execute", "exists", "exit", "external", "fetch", "file", "fillfactor", "for", "foreign", "freetext", "freetexttable", "from", "full", "function", "goto", "grant", "group", "having", "holdlock", "identity", "identity_insert", "identitycol", "if", "in", "index", "inner", "insert", "intersect", "into", "is", "join", "key", "kill", "left", "like", "lineno", "load", "merge", "national", "nocheck", "nonclustered", "not", "null", "nullif", "of", "off", "offsets", "on", "open", "opendatasource", "openquery", "openrowset", "openxml", "option", "or", "order", "outer", "over", "percent", "pivot", "plan", "precision", "primary", "print", "proc", "procedure", "public", "raiserror", "read", "readtext", "reconfigure", "references", "replication", "restore", "restrict", "return", "revert", "revoke", "right", "rollback", "rowcount", "rowguidcol", "rule", "save", "schema", "securityaudit", "select", "semantickeyphrasetable", "semanticsimilaritydetailstable", "semanticsimilaritytable", "session_user", "set", "setuser", "shutdown", "some", "statistics", "system_user", "table", "tablesample", "textsize", "then", "to", "top", "tran", "transaction", "trigger", "truncate", "try_convert", "tsequal", "union", "unique", "unpivot", "update", "updatetext", "use", "user", "values", "varying", "view", "waitfor", "when", "where", "while", "with", "within group", "writetext")

//  override protected def limitOffsetToken(
//    query: Statement
//  )(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy): Tokenizer[(Option[Ast], Option[Ast])] =
//    Tokenizer[(Option[Ast], Option[Ast])] {
//      case (Some(limit), None)         => stmt"TOP (${limit.token}) $query"
//      case (Some(limit), Some(offset)) => stmt"$query OFFSET ${offset.token} ROWS FETCH FIRST ${limit.token} ROWS ONLY"
//      case (None, Some(offset))        => stmt"$query OFFSET ${offset.token} ROWS"
//      case other                       => super.limitOffsetToken(query).token(other)
//    }

  override fun limitOffsetToken(query: Statement, limit: XR.Expression?, offset: XR.Expression?): Token =
    when {
      limit != null && offset == null -> +"TOP (${limit.token}) $query"
      limit != null && offset != null -> +"$query OFFSET ${offset.token} ROWS FETCH FIRST ${limit.token} ROWS ONLY"
      limit == null && offset != null -> +"$query OFFSET ${offset.token} ROWS"
      else -> query
    }


  override fun xrReturningTokenImpl(query: XR.Returning): Token =
    when {
      query.kind is XR.Returning.Kind.Expression && query.action is XR.Insert ->
        tokenizeOutputtingInsert(query.kind, query.action)
      query.kind is XR.Returning.Kind.Expression && query.action is XR.Update ->
        tokenizeOutputtingUpdate(query.kind, query.action)
      query.kind is XR.Returning.Kind.Expression && query.action is XR.Delete ->
        tokenizeOutputtingDelete(query.kind, query.action)
      // If the returning-kind is Keys then we don't use the output-clause and rely on Sttement.generatedKeys (and similar APIs)
      else ->
        super<SqlIdiom>.xrReturningTokenImpl(query)
    }

  fun tokenizeOutputtingInsert(expr: XR.Returning.Kind.Expression, insert: XR.Insert): Token = run {
    val newAlias = insert.coreAlias().copy(name = "INSERTED")
    val returningClauseToken = protractReturning(expr, newAlias)
    val query = insert.query as? XR.Entity ?: xrError("Insert query must be an entity but found: ${insert.query}")
    val insertBase = tokenizeInsertBase(insert)
    +"${insertBase} OUTPUT ${returningClauseToken}"
  }

  fun tokenizeOutputtingUpdate(expr: XR.Returning.Kind.Expression, update: XR.Update): Token = run {
    val newAlias = update.coreAlias().copy(name = "INSERTED")
    val returningClauseToken = protractReturning(expr, newAlias)
    fun updateBase() = +"${tokenizeUpdateBase(update)} OUTPUT ${returningClauseToken}"
    when {
      update.query is XR.Filter && update.query.head is XR.Entity ->
        +"${updateBase()} WHERE ${update.query.token}"
      update.query is XR.Entity ->
        updateBase()
      else ->
        xrError("Invalid query-clause in an Update. It can only be a XR Filter or Entity but was:\n${update.query.showRaw()}")
    }
  }

  fun tokenizeOutputtingDelete(expr: XR.Returning.Kind.Expression, delete: XR.Delete): Token = run {
    val newAlias = delete.coreAlias().copy(name = "DELETED")
    val returningClauseToken = protractReturning(expr, newAlias)
    fun deleteBase() = +"${tokenizeDeleteBase(delete)} OUTPUT ${returningClauseToken}"
    when {
      delete.query is XR.Filter && delete.query.head is XR.Entity ->
        +"${deleteBase()} WHERE ${delete.query.token}"
      delete.query is XR.Entity ->
        deleteBase()
      else ->
        xrError("Invalid query-clause in a Delete. It can only be a XR Filter or Entity but was:\n${delete.query.showRaw()}")
    }
  }

  override fun opBinaryTokenImpl(opImpl: BinaryOperator): Token = with (opImpl) {
    when (this) {
      is OP.strPlus -> +"+"
      else -> super<SqlIdiom>.opBinaryTokenImpl(opImpl)
    }
  }

}


//
// TODO add this warning
//  override implicit def sqlQueryTokenizer(implicit
//    astTokenizer: Tokenizer[Ast],
//    strategy: NamingStrategy,
//    idiomContext: IdiomContext
//  ): Tokenizer[SqlQuery] =
//    Tokenizer[SqlQuery] {
//      case flatten: FlattenSqlQuery if flatten.orderBy.isEmpty && flatten.offset.nonEmpty =>
//        fail(s"SQLServer does not support OFFSET without ORDER BY")
//      case other => super.sqlQueryTokenizer.token(other)
//    }
//
//  override protected def actionTokenizer(insertEntityTokenizer: Tokenizer[Entity])(implicit
//    astTokenizer: Tokenizer[Ast],
//    strategy: NamingStrategy,
//    idiomContext: IdiomContext
//  ): Tokenizer[ast.Action] =
//    Tokenizer[ast.Action] {
//      // Update(Filter(...)) and Delete(Filter(...)) usually cause a table alias i.e. `UPDATE People <alias> SET ... WHERE ...` or `DELETE FROM People <alias> WHERE ...`
//      // since the alias is used in the WHERE clause. This functionality removes that because SQLServer doesn't support aliasing in actions.
//      case Update(Filter(table: Entity, x, where), assignments) =>
//        stmt"UPDATE ${table.token} SET ${assignments.token} WHERE ${where.token}"
//      case Delete(Filter(table: Entity, x, where)) =>
//        stmt"DELETE FROM ${table.token} WHERE ${where.token}"
//      case other => super.actionTokenizer(insertEntityTokenizer).token(other)
//    }
//}
//
//object SQLServerDialect extends SQLServerDialect
