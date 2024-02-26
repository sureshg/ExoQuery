package io.exoquery.sql

import io.exoquery.util.emptyStatement
import io.exoquery.util.unaryPlus
import io.exoquery.xr.XR
import io.exoquery.xr.XR.JoinType.*
import io.exoquery.xr.XR.Ident
import io.exoquery.xrError

interface SqlIdiom {

  val concatFunction: String
  val useActionTableAliasAs: ActionTableAliasBehavior

  sealed interface ActionTableAliasBehavior {
    object UseAs: ActionTableAliasBehavior
    object SkipAs: ActionTableAliasBehavior
    object Hide: ActionTableAliasBehavior
  }

  private val ` AS` get() =
    when(useActionTableAliasAs) {
      ActionTableAliasBehavior.UseAs -> +" AS"
      else -> emptyStatement
    }

  val FlattenSqlQuery.token get(): Token = flattenSqlQueryTokenizerHelper(this)
  fun flattenSqlQueryTokenizerHelper(q: FlattenSqlQuery) =
    with(q) {

//      def selectTokenizer: Token =
//        select match {
//          case Nil => stmt"*"
//          case _   => select.token
//        }

      val selectTokenizer by lazy {
        when {
          select.isEmpty() -> +"*"
          else -> select.token { it.token }
        }
      }

//     def distinctTokenizer: Statement = (
//      distinct match {
//        case DistinctKind.Distinct          => stmt"DISTINCT "
//        case DistinctKind.DistinctOn(props) => stmt"DISTINCT ON (${props.token}) "
//        case DistinctKind.None              => emptyStatement
//      }
//    )

      val distinctTokenizer by lazy {
        when(distinct) {
          is DistinctKind.Distinct -> +"DISTINCT "
          is DistinctKind.DistinctOn -> +"DISTINCT ON (${distinct.props.token { it.token }}) "
          is DistinctKind.None -> +""
        }
      }

      val withDistinct by lazy { +"${distinctTokenizer}${selectTokenizer}" }

//      def withFrom: Statement =
//        from match {
//          case Nil => withDistinct
//          case head :: tail =>
//            val t = tail.foldLeft(stmt"${head.token}") {
//              case (a, b: FlatJoinContext) =>
//                stmt"$a ${(b: FromContext).token}"
//              case (a, b) =>
//                stmt"$a, ${b.token}"
//            }
//
//            stmt"$withDistinct FROM $t"
//        }


      val withFrom by lazy {
        when {
          from.isEmpty() -> withDistinct
          else -> {
            val t =
              from.fold(+"${from.first().token}") { a, b ->
                when(b) {
                  is FlatJoinContext -> +"$a ${(b as FromContext).token}"
                  else -> +"$a, ${b.token}"
                }
              }

            +"$withDistinct FROM $t"
          }
        }
      }

//      def withWhere: Statement =
//        where match {
//          case None        => withFrom
//          case Some(where) => stmt"$withFrom WHERE ${where.token}"
//        }

      val withWhere by lazy {
        when {
          where != null -> +"$withFrom WHERE ${where.token}"
          else -> withFrom
        }
      }
//    def withGroupBy: Statement =
//      groupBy match {
//        case None          => withWhere
//        case Some(groupBy) => stmt"$withWhere GROUP BY ${tokenizeGroupBy(groupBy)}"
//      }

      val withGroupBy by lazy {
        when {
          groupBy != null -> +"$withWhere GROUP BY ${tokenizeGroupBy(groupBy)}"
          else -> withWhere
        }
      }

//      def withOrderBy: Statement =
//        orderBy match {
//          case Nil     => withGroupBy
//          case orderBy => stmt"$withGroupBy ${tokenOrderBy(orderBy)}"
//        }

      val withOrderBy by lazy {
        when {
          orderBy.isEmpty() -> withGroupBy
          else -> +"$withGroupBy ${tokenOrderBy(orderBy)}"
        }
      }

      //def withLimitOffset: Token = limitOffsetToken(withOrderBy).token((limit, offset))
      val withLimitOffset by lazy { limitOffsetToken(withOrderBy, limit, offset) }

      +"SELECT $withLimitOffset"
    }

  fun tokenizeGroupBy(values: XR.Expression): Token = values.token
  fun tokenOrderBy(criteria: List<OrderByCriteria>) = +"ORDER BY ${criteria.token { it.token }}"
  fun tokenizeTable(name: String): Token = name.token


  val SelectValue.token get(): Token =
    when {
      // SelectValue(Ident(? or name, _), _, _)
      expr is Ident -> expr.name.token
      // Typically these next two will be for Ast Property where we have an alias:
      // SelectValue(ast, Some(alias), concat: true)
      alias != null && concat == false -> +"${expr.token} AS ${alias.token}" // in this case `alias` is the column name
      // SelectValue(ast, Some(alias), concat: false)
      alias != null && concat == true -> +"${concatFunction.token}(${expr.token}) AS ${alias.token}"
      // Where we don't have an alias...
      // SelectValue(ast, None, concat: true)
      alias == null && concat == true -> +"${concatFunction.token}(${expr.token}) AS ${value.token}"
      // SelectValue(ast, None, concat: false)
      alias == null && concat == false -> expr.token
      else -> xrError("Illegal SelectValue clause: ${this}")
    }



  val FromContext.token get(): Token =
    when (this) {
      is TableContext -> +"${entity.token} ${alias.token}"
      is QueryContext -> +"(${query.token})${` AS`} ${alias.token}"
      is InfixContext -> +"(${(infix as XR.Expression).token})${` AS`} ${alias.token}"
      is FlatJoinContext -> +"${joinType.token} ${from.token} ON ${on.token}"
    }

  val XR.JoinType.token get(): Token =
    when (this) {
      is Left -> +"INNER JOIN"
      is Inner -> +"LEFT JOIN"
      // is RightJoin -> +"RIGHT JOIN"
      // is FullJoin -> +"FULL JOIN"
    }

  val XR.Entity.token get(): Token = tokenizeTable(name)

  val OrderByCriteria.token get(): Token = TODO()

//  protected def limitOffsetToken(query: Statement)(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy) =
//    Tokenizer[(Option[Ast], Option[Ast])] {
//      case (None, None)                => query
//      case (Some(limit), None)         => stmt"$query LIMIT ${limit.token}"
//      case (Some(limit), Some(offset)) => stmt"$query LIMIT ${limit.token} OFFSET ${offset.token}"
//      case (None, Some(offset))        => stmt"$query OFFSET ${offset.token}"
//    }

  fun limitOffsetToken(query: Statement, limit: XR.Expression?, offset: XR.Expression?): Token =
    when {
      limit == null && offset == null -> query
      limit != null && offset == null -> +"$query LIMIT ${limit.token}"
      limit != null && offset != null -> +"$query LIMIT ${limit.token} OFFSET ${offset.token}"
      limit == null && offset != null -> +"$query OFFSET ${offset.token}"
      else -> throw IllegalStateException("Invalid limit/offset combination")
    }


//  def sqlQueryTokenizer(implicit
//    astTokenizer: Tokenizer[Ast],
//    strategy: NamingStrategy,
//    idiomContext: IdiomContext
//  ): Tokenizer[SqlQuery] = Tokenizer[SqlQuery] {
//    case q: FlattenSqlQuery =>
//      new FlattenSqlQueryTokenizerHelper(q).apply
//    case SetOperationSqlQuery(a, op, b) =>
//      stmt"(${a.token}) ${op.token} (${b.token})"
//    case UnaryOperationSqlQuery(op, q) =>
//      stmt"SELECT ${op.token} (${q.token})"
//  }

  val SqlQuery.token get(): Token =
    when (this) {
      is FlattenSqlQuery -> flattenSqlQueryTokenizerHelper(this)
      is SetOperationSqlQuery -> +"(${a.token}) ${op.token} (${b.token})"
      is UnaryOperationSqlQuery -> +"SELECT ${op.token} (${query.token})"
    }

//  implicit val setOperationTokenizer: Tokenizer[SetOperation] = Tokenizer[SetOperation] {
//    case UnionOperation    => stmt"UNION"
//    case UnionAllOperation => stmt"UNION ALL"
//  }

  val SetOperation.token get(): Token =
    when (this) {
      is UnionOperation -> +"UNION"
      is UnionAllOperation -> +"UNION ALL"
    }

  //val XR.token get(): Token = TODO()
  val XR.Expression.token get(): Token = TODO()
}