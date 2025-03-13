package io.exoquery.norm.verify

import io.exoquery.sql.*
import io.exoquery.xr.XR

class VerifySqlQuery {
  fun invoke(query: SqlQuery): FreeVariables.Result =
    verify(query)

  private fun verify(query: SqlQuery): FreeVariables.Result =
    when (query) {
      is FlattenSqlQuery -> verifyFlatJoin(query)
      is SetOperationSqlQuery -> verify(query.a) + verify(query.b)
      is UnaryOperationSqlQuery -> verify(query.query)
    }

//  def apply(query: SqlQuery): Option[String] =
//    verify(query).map(_.toString)
//
//  private def verify(query: SqlQuery): Option[InvalidSqlQuery] =
//    query match {
//      // if it's just an infix query no more verification to do
//      case p: TopInfixQuery               => None
//      case q: FlattenSqlQuery             => verify(q)
//      case SetOperationSqlQuery(a, op, b) => verify(a).orElse(verify(b))
//      case UnaryOperationSqlQuery(op, q)  => verify(q)
//    }

  private fun verifyFlatJoinDeep(q: FlattenSqlQuery): FreeVariables.Result {
    val invalidAliases = mutableSetOf<IdentName>()
    fun loop(l: List<FromContext>, available: Set<IdentName>): Set<IdentName> =
      l.fold(available) { av, ctx ->
        when (ctx) {
          is TableContext -> setOf(ctx.aliasIdent().asIdName())
          is ExpressionContext -> setOf(ctx.aliasIdent().asIdName())
          is QueryContext -> setOf(ctx.aliasIdent().asIdName())
          is FlatJoinContext -> {
            val nav = av + loop(listOf(ctx.from), av)
            val free = FreeVariables(ctx.on).map { it }
            val invalid = free - nav
            invalidAliases += invalid
            nav
          }
        }
      }
    // run the actual loop
    loop(q.from, setOf())
    return invalidAliases.toFreeVarsResult()
  }

  fun Set<IdentName>.toFreeVarsResult(): FreeVariables.Result =
    when {
      isEmpty() -> FreeVariables.Result.None
      else -> FreeVariables.Result.Detected(toSet())
    }

  //  private def verifyFlatJoins(q: FlattenSqlQuery) = {
//
//    def loop(l: List[FromContext], available: Set[String]): Set[String] =
//      l.foldLeft(available) {
//        case (av, TableContext(_, alias)) => Set(alias)
//        case (av, InfixContext(_, alias)) => Set(alias)
//        case (av, QueryContext(_, alias)) => Set(alias)
//        case (av, FlatJoinContext(_, a, on)) =>
//          val nav     = av ++ loop(a :: Nil, av)
//          val free    = FreeVariables(on).map(_.name)
//          val invalid = free -- nav
//          require(
//            invalid.isEmpty,
//            s"Found an `ON` table reference of a table that is not available: $invalid. " +
//              "The `ON` condition can only use tables defined through explicit joins."
//          )
//          nav
//      }
//    loop(q.from, Set())
//  }

  private fun verifyFlatJoin(q: FlattenSqlQuery): FreeVariables.Result {
    val invalidWithin = verifyFlatJoinDeep(q)
    val aliases = q.from.flatMap { aliases(it) }

    fun verifyAst(ast: XR): FreeVariables.Result = run {
      val freeVariables = (FreeVariables(ast) - aliases).toList()
      freeVariables.toSet().toFreeVarsResult()
    }

//  private def verify(query: FlattenSqlQuery): Option[InvalidSqlQuery] = {
//    verifyFlatJoins(query)
//    val aliases = query.from.flatMap(this.aliases).map(IdentName(_)) :+ IdentName("*") :+ IdentName("?")
//
//    def verifyAst(ast: Ast) = {
//      val freeVariables = (FreeVariables(ast) -- aliases).toList
//      freeVariables match {
//        case Nil => None
//        case free =>
//          Some(
//            Error(free.map(f => Ident(f.name, Quat.Value)), ast)
//          ) // Quat is not actually needed here just for the sake of the Error Ident
//      }
//    }
//

    // Recursively expand children until values are fully flattened. Identities in all these should
    // be skipped during verification.
    fun expandSelect(sv: SelectValue): List<SelectValue> =
      when (val ast = sv.expr) {
        is XR.Product -> ast.fields.map { SelectValue(it.second) }.flatMap { expandSelect(it) }
        else -> listOf(sv)
      }


//    def expandSelect(sv: SelectValue): List[SelectValue] =
//      sv.ast match {
//        case CaseClass(_, values) => values.map(v => SelectValue(v._2)).flatMap(expandSelect(_))
//        case _                    => List(sv)
//      }
//

    fun XR.Expression?.verifyOrSkip(): FreeVariables.Result =
      this?.let { verifyAst(it) } ?: FreeVariables.Result.None
    fun List<XR.Expression>.verifyOrSkip(): FreeVariables.Result =
      this.map { it.verifyOrSkip() }.reduce { a, b -> a + b }

    val freeVariableErrors =
      q.where.verifyOrSkip() +
        q.orderBy.map { it.ast }.verifyOrSkip() +
        q.limit.verifyOrSkip() +
        q.select
          .flatMap { expandSelect(it) }
          .map { it.expr }
          .filterNot { it is XR.Ident }
          .verifyOrSkip() +
        q.from.map {
          when (it) {
            is FlatJoinContext -> it.on.verifyOrSkip()
            is QueryContext -> verify(it.query)
            else -> FreeVariables.Result.None
          }
        }.reduce { a, b -> a + b }

    return freeVariableErrors
  }


// Scala:
//object VerifySqlQuery {
//
//    val freeVariableErrors: List[Error] =
//      query.where.flatMap(verifyAst).toList ++
//        query.orderBy.map(_.ast).flatMap(verifyAst) ++
//        query.limit.flatMap(verifyAst) ++
//        query.select
//          .flatMap(expandSelect(_)) // Expand tuple select clauses so their top-level identities are skipped
//          .map(_.ast)
//          .filterNot(_.isInstanceOf[Ident])
//          .flatMap(verifyAst) ++
//        query.from.flatMap {
//          case j: FlatJoinContext => verifyAst(j.on)
//          case _                  => Nil
//        }
//
//    // (in the Kotlin doing this together with the query.from.flatMap)
//    val nestedErrors =
//      query.from.collect { case QueryContext(query, alias) =>
//        verify(query).map(_.errors)
//      }.flatten.flatten
//
//    (freeVariableErrors ++ nestedErrors) match {
//      case Nil    => None
//      case errors => Some(InvalidSqlQuery(errors))
//    }
//  }
//
//

  private fun aliases(s: FromContext): List<IdentName> =
    when (s) {
      is TableContext -> listOf(s.aliasIdent().asIdName())
      is QueryContext -> listOf(s.aliasIdent().asIdName())
      is ExpressionContext -> listOf(s.aliasIdent().asIdName())
      is FlatJoinContext -> aliases(s.from)
    }

//  private def aliases(s: FromContext): List[String] =
//    s match {
//      case s: TableContext    => List(s.alias)
//      case s: QueryContext    => List(s.alias)
//      case s: InfixContext    => List(s.alias)
//      case s: FlatJoinContext => aliases(s.a)
//    }
}
