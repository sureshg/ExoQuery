package io.exoquery.sql

import io.exoquery.printing.HasPhasePrinting
import io.exoquery.util.*
import io.exoquery.xr.*
import io.exoquery.xr.OP.*
import io.exoquery.xr.OP.and
import io.exoquery.xr.OP.or
import io.exoquery.xr.XR.BinaryOp
import io.exoquery.xr.XR.Ordering.*
import io.exoquery.xr.XR.Visibility.*
import io.exoquery.xr.XR.JoinType.*
import io.exoquery.xr.XR.Ident
import io.exoquery.xr.XR.Const.Null
import io.exoquery.xr.XR.ParamType
import io.exoquery.xrError

abstract class SqlIdiom: HasPhasePrinting {

  override val traceType: TraceType = TraceType.SqlNormalizations
  abstract val concatFunction: String
  abstract val useActionTableAliasAs: ActionTableAliasBehavior

  val aliasSeparator: String get() = "_"
  open fun joinAlias(alias: List<String>): String = alias.joinToString(aliasSeparator)

  override val trace: Tracer by lazy { Tracer(traceType, traceConf, 1) }

  protected fun normalizeQuery(xr: XR.Query) =
    SqlNormalize(traceConf = traceConf, disableApplyMap = false)(xr)

  inline fun ((SqlQuery) -> SqlQuery).andThen(phaseTitle: String, crossinline f: (SqlQuery) -> SqlQuery): (SqlQuery) -> SqlQuery  =
    { qRaw ->
      val q = this(qRaw)
      val label = traceConf.phaseLabel?.let { " (${it})" } ?: ""
      demarcate("Phase: ${phaseTitle}${label}", q)
      val output = f(q)
      output
    }

  fun prepareQuery(xr: XR.Query): SqlQuery {
    val q = normalizeQuery(xr)
    val sqlQuery = SqlQueryApply(traceConf)(q)
    val root = { q: SqlQuery -> q }
    val output =
      root
        .andThen("SqlQueryApply") { it -> it }
        .andThen("ValueizeSingleSelects") { ValueizeSingleLeafSelects()(it, q.type) }
        .andThen("ExpandNestedQueries") { ExpandNestedQueries(::joinAlias)(it) }
        .andThen("RemoveExtraAlias") { RemoveExtraAlias()(it) }
        .invoke(sqlQuery)

    // TODO need to implement free-variables checking

    return output
  }

  // TODO also return the params from there
  fun processQuery(xr: XR.Query): Token {
    val q = prepareQuery(xr)
    return q.token
  }

  fun processAction(xr: XR.Action): Token {
    return xr.token
  }

  fun translate(xr: XR.Query) =
    prepareQuery(xr).token.show(Renderer(true, true, null))

  private fun translateBasic(xr: XR): Token {
    // TODO caching
    return when {
      xr is XR.Query -> {
        val normed = normalizeQuery(xr)
        val tokenized = normed.token
        trace("Tokenized SQL: ${tokenized}").andLog()
        tokenized
      }
      else ->
        xr.token
    }
  }

  fun show(xr: XR, pretty: Boolean = false): String {
    val sqlString = translateBasic(xr).token.toString()
    // TODO KMP-out an the vertical-blank sql formatter for JVM via `expect`
    val formatted = sqlString
    return formatted
  }


  val productAggregationToken: ProductAggregationToken get() = ProductAggregationToken.Star



  sealed interface ActionTableAliasBehavior {
    object UseAs: ActionTableAliasBehavior
    object SkipAs: ActionTableAliasBehavior
    object Hide: ActionTableAliasBehavior
  }

  // Very few cases actually end-up going to this top level i.e.
  // the only case so far is the params of an infix. This is because typically
  // the only XR things remaining by this point are the expressions inside the SelectValues
  val XR.token get(): Token =
    when (this) {
      is XR.Expression -> token
      is XR.Query -> token
      // is XR.Action -> this.lift()
      is XR.Branch, is XR.Variable ->
        xrError("All instances of ${this::class.qualifiedName} should have been beta-reduced out by now.")
      is XR.Action -> TODO()
      is XR.Assignment -> TODO()
    }

  val XR.Expression.token get(): Token =
    when (this) {
      is BinaryOp      -> token
      is XR.UnaryOp       -> token
      is XR.Const         -> token
      is XR.Free         -> token
      is XR.Product       -> token
      is XR.Property      -> token
      is Ident            -> token
      is XR.When          -> token
      is XR.GlobalCall    -> token
      is XR.MethodCall    -> token
      is XR.QueryToExpr       -> token
      is XR.FunctionN, is XR.FunctionApply, is XR.Block ->
        xrError("Malformed or unsupported construct: $this.")
      is XR.TagForParam ->
        when (this.paramType) {
          is ParamType.Single -> ParamSingleToken(this.id)
          is ParamType.Multi  -> ParamMultiToken(this.id)
        }
      is XR.TagForSqlExpression ->
        xrError("Internal error. All instance of TagFOrSqlExpressio should have been spliced earlier.")
    }

  val XR.Ident.token get(): Token = name.token

  /**
   * For example something like `people.map(_.age).avg` would be something like `people.map(_.age).value.avg`
   * This should in-turn take advantage of GlobalCall so it shold really be something like: `people.map(_.age).value.globalCall("kotlin.Int", "avg")`.
   * In the AST this would be something like: `GlobalCall("kotlin.Int", "avg", listOf(QueryToExpr( <people.map(_.age)> )))`.
   * Now on an SQL level we don't
   */
  val XR.QueryToExpr.token get(): Token = head.token

  fun tokenizeMethodCallFqName(name: XR.FqName): Token =
    // TODO this should be per dialect, maybe even configureable. I.e. every dialect should have it's supported MethodCall functions
    //      this list of method-names should techinically be available to the parser when it is parsing so appropriate
    //      cannot-parse exceptions will be thrown if it is not. We could also introduce a "Promiscuous-Parser" mode where that is disabled.
    when {
      name.name == "split" -> "split".token
      name.name == "startsWith" -> "startsWith".token
      name.name == "split" -> "split".token
      name.name == "toUpperCase" -> "toUpperCase".token
      name.name == "toLowerCase" -> "toLowerCase".token
      name.name == "toLong" -> "toLong".token
      name.name == "toInt" -> "toInt".token
      else -> throw IllegalArgumentException("Unknown method: ${name.toString()}")
    }

  fun tokenizeGlobalCallFqName(name: XR.FqName): Token =
    // TODO this should be per dialect, maybe even configureable. I.e. every dialect should have it's supported MethodCall functions
    when {
      name.name == "min" -> "min".token
      name.name == "max" -> "max".token
      name.name == "avg" -> "avg".token
      name.name == "sum" -> "sum".token
      name.name == "size" -> "size".token
      else -> throw IllegalArgumentException("Unknown global method: ${name.toString()}")
    }

  fun stringStartsWith(str: XR.U.QueryOrExpression, prefix: XR.U.QueryOrExpression): Token =
    +"starts_with(${str.token}, ${prefix.token})"

  fun XR.U.QueryOrExpression.stringConversionMapping(name: String): Token = run {
    val head = this
    when (name) {
      "toLong" -> +"CAST(${head.token} AS BIGINT)"
      "toInt" -> +"CAST(${head.token} AS INTEGER)"
      "toShort" -> +"CAST(${head.token} AS SMALLINT)"
      "toDouble" -> +"CAST(${head.token} AS DOUBLE PRECISION)"
      "toFloat" -> +"CAST(${head.token} AS REAL)"
      "toBoolean" -> +"CAST(${head.token} AS BOOLEAN)"
      "toString" -> +"${head.token}"
      else -> throw IllegalArgumentException("Unknown conversion function: ${name}")
    }
  }

  fun XR.U.QueryOrExpression.wholeNumberConversionMapping(name: String): Token = run {
    val head = this
    when (name) {
      "toDouble" -> +"CAST(${head.token} AS DOUBLE PRECISION)"
      "toFloat" -> +"CAST(${head.token} AS REAL)"
      "toBoolean" -> +"CAST(${head.token} AS BOOLEAN)"
      "toString" -> +"CAST(${head.token} AS ${varcharType()})"
      // toInt, toLong, toShort reply in implicit casting
      else -> +"${head.token}"
    }
  }

  fun XR.U.QueryOrExpression.floatConversionMapping(name: String): Token = run {
    val head = this
    when (name) {
      "toLong" -> +"CAST(${head.token} AS BIGINT)"
      "toInt" -> +"CAST(${head.token} AS INTEGER)"
      "toShort" -> +"CAST(${head.token} AS SMALLINT)"
      "toBoolean" -> +"CAST(${head.token} AS BOOLEAN)"
      "toString" -> +"CAST(${head.token} AS ${varcharType()})"
      // toFloat, toDouble reply in implicit casting
      else -> +"${head.token}"
    }
  }

  // Certain dialects require varchar sizes so allow this to be overridden
  fun varcharType(): Token = "VARCHAR".token

  // TODO needs lots of refinement
  val XR.MethodCall.token get(): Token = run {
    val argsToken = (listOf(head) + args).map { it -> it.token }.mkStmt()
    when {
      // NOTE: Perhaps we should check that io.exoquery.Params is the host-type? Need to think about various implications of being more strict
      name == "contains" -> +"${args.first().token} ${"IN".token} (${head.token})"

      // rely on implicit-casts for numeric conversion
      originalHostType.isWholeNumber() && name.isConverterFunction() ->
        head.wholeNumberConversionMapping(name)

      originalHostType.isFloatingPoint() && name.isConverterFunction() ->
        head.floatConversionMapping(name)

      originalHostType == CID.kotlin_String -> {
        when {
          // Cast strings to numeric types if needed
          name.isConverterFunction() -> head.stringConversionMapping(name)
          name == "substring" -> +"SUBSTRING(${head.token}, ${args.first().token}, ${args.last().token})"
          name == "startsWith" -> stringStartsWith(head, args.first())
          name == "uppercase" -> +"UPPER(${head.token})"
          name == "lowercase" -> +"LOWER(${head.token})"
          name == "left" -> +"LEFT(${head.token}, ${args.first().token})"
          name == "right" -> +"RIGHT(${head.token}, ${args.first().token})"
          name == "replace" -> +"REPLACE(${head.token}, ${args.first().token}, ${args.last().token})"
          else -> xrError("Unknown or invalid XR.MethodCall method: ${name} in the expression:\n${this.showRaw()}")
        }
      }
      head is XR.Query && name == "isNotEmpty" -> +"EXISTS (${head.token})"
      head is XR.Query && name == "isEmpty" -> +"NOT EXISTS (${head.token})"
      // in correlated-query situations where we have an Query-level aggregator inside of a filter e.g.
      // `people.filter(p => addresses.map(a => a.ownerId).min == p.id)`. In that case we need ot go back
      // to the top-level with the inner-query and run the SqlQuery expasion on the head XR.Query object
      // This will go into XR.Query.token and which will call the Query expansion into SqlQuery.
      // Doing all of this runs the potential risk of recursing forever if SqlQuery returns a SelectValue with the full
      // query expression so need make sure that doesn't happen there in SqlQuery.
      head is XR.Query && callType == XR.CallType.QueryAggregator -> {
        scopedQueryTokenizer(this as XR.Query)
      }
      // Need to think about situations where it is a CallType.PureFunction/ImpureFunction and see how it needs to be expanded. Something with sub-expansion
      // might be necessary e.g. sub-expading the XR.Query with SqlQuery if needed

      else -> xrError("Unknown or invalid XR.MethodCall method: ${name} in the expression:\n${this.showRaw()}")
    }
  }

  fun tokenizeSelectAggregator(call: XR.MethodCall): Statement {
    val op = call.name
    val expr = call.head
    return when {
      // Aggregation(op, Ident(id, _: Quat.Product))
      expr is Ident && expr.type is XRType.Product -> +"${op.token}(${makeProductAggregationToken(expr.name)})"
      // Not too many cases of this. Can happen if doing a leaf-level infix inside of a select clause. For example in postgres:
      // `sql"unnest(array['foo','bar'])".as[Query[Int]].groupBy(p => p).map(ap => ap._2.max)` which should yield:
      // SELECT MAX(inf) FROM (unnest(array['foo','bar'])) AS inf GROUP BY inf
      expr is XR.Ident -> +"${op.token}(${expr.token})"
      expr is XR.Product -> +"${op.token}(*)"
      // In ExoQuery Distinct is a type of Query so it cannot occur in an aggregation expression
      //expr is XR.Distinct -> +"${op.token}(DISTINCT ${expr.ast.token})"
      else -> +"${op.token}(${expr.token})"
    }
  }


  val XR.GlobalCall.token get(): Token = run {
    val argsToken = args.map { it -> it.token }.mkStmt()
    +"${name.name}(${argsToken})"
  }



  val XR.When.token get(): Token = run {
    val whenThens = branches.map { it -> +"WHEN ${it.cond.token} THEN ${it.then.token}" }
    +"CASE ${whenThens.mkStmt(" ")} ELSE ${orElse.token} END"
  }

  val XR.Const.token get(): Token =
    when(this) {
      is XR.Const.Boolean -> +"${value.toString().token}"
      is XR.Const.Byte    -> +"${value.toString().token}" // use kotlin 1.9.22 stlib to have this: +"${value.toHexString()}"
      is XR.Const.Char    -> +"'${value.toString().token}'"
      is XR.Const.Double  -> +"${value.toString().token}"
      is XR.Const.Float   -> +"${value.toString().token}"
      is XR.Const.Int     -> +"${value.toString().token}"
      is XR.Const.Long    -> +"${value.toString().token}"
      is XR.Const.Null       -> +"null"
      is XR.Const.Short   -> +"${value.toString().token}"
      is XR.Const.String  -> +"'${value.toString().token}'"
    }

  // Typically this will be a tuple of some sort, just converts the elements into a list
  // For dialects of SQL like Spark that select structured data this needs special handling
  val XR.Product.token get(): Token =
    fields.map { it -> it.second.token }.mkStmt()

  val XR.Query.token get(): Token =
    when (this) {
      is XR.ExprToQuery -> head.token
      else -> {
        // This case typically happens when you have a select inside of an insert
        // infix or a set operation (e.g. query[Person].exists).
        // have a look at the SqlDslSpec `forUpdate` and `insert with subselects` tests
        // for more details.
        // Right now we are not removing extra select clauses here (via RemoveUnusedSelects) since I am not sure what
        // kind of impact that could have on selects. Can try to do that in the future.
        if (Globals.querySubexpand) {
          val nestedExpanded = ExpandNestedQueries(::joinAlias)(SqlQueryApply(traceConf)(this))
          // TODO Need to implement
          //RemoveExtraAlias(strategy)(nestedExpanded).token
          nestedExpanded.token
        } else
          SqlQueryApply(traceConf)(this).token
      }
    }


  private val ` AS` get() =
    when(useActionTableAliasAs) {
      ActionTableAliasBehavior.UseAs -> +" AS"
      else -> emptyStatement
    }

  val FlattenSqlQuery.token get(): Token = run {

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
              from.drop(1).fold(+"${from.first().token}") { a, b ->
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

      +"SELECT ${withLimitOffset}"
    }

  fun tokenizeGroupBy(values: XR.Expression): Token = values.token
  fun tokenOrderBy(criteria: List<OrderByCriteria>) = +"ORDER BY ${criteria.token { it.token }}"
  fun tokenizeTable(name: String): Token = name.token
  fun tokenizeAlias(alias: List<String>): Token = StringToken(this.joinAlias(alias))


  val SelectValue.token get(): Token =
    when {
      // SelectValue(Ident(? or name, _), _, _)
      expr is Ident -> expr.name.token
      // Typically these next two will be for Ast Property where we have an alias:
      // SelectValue(ast, Some(alias), concat: true)
      alias.isNotEmpty() && concat == false -> +"${expr.token} AS ${tokenizeAlias(alias)}" // in this case `alias` is the column name
      // SelectValue(ast, Some(alias), concat: false)
      alias.isNotEmpty() && concat == true -> +"${concatFunction.token}(${expr.token}) AS ${tokenizeAlias(alias)}"
      // Where we don't have an alias...
      // SelectValue(ast, None, concat: true)
      alias.isEmpty() && concat == true -> +"${concatFunction.token}(${expr.token}) AS ${expr.token}"
      // SelectValue(ast, None, concat: false)
      alias.isEmpty() && concat == false -> expr.token

      // NOTE: In Quill there was an aggregation-tokenizer here because Aggregation was a subtype of Query
      // (because aggregations were monadic). This is not the case in ExoQuery where aggregations
      // are just a type of expression. So this case is a subtype of XR.Expression.

      else -> xrError("Illegal SelectValue clause: ${this}")
    }


  fun makeProductAggregationToken(id: String) =
    when(productAggregationToken) {
      ProductAggregationToken.Star            -> +"*"
      ProductAggregationToken.VariableDotStar -> +"${id.token}.*"
    }

  // x is Any are technically useless checks but then entire logic here is much simpler to understand
  // with them enabled.  I'm not sure if the compiler will optimize them out or not but I
  // do not thing it will make a significant performance penalty.
  @Suppress("USELESS_IS_CHECK")
  val XR.BinaryOp.token get(): Token =
    when {
      a is Any  && op is `==` && b is Null -> +"${scopedTokenizer(a)} IS NULL"
      a is Null && op is `==` && b is Any  -> +"${scopedTokenizer(b)} IS NULL"
      a is Any  && op is `!=` && b is Null -> +"${scopedTokenizer(a)} IS NOT NULL"
      a is Null && op is `!=` && b is Any  -> +"${scopedTokenizer(b)} IS NOT NULL"

      a is Any && op is `and` && b is Any ->
        when {
          // (a1 || a2) && (b1 || b2) i.e. need parens around the a and b
          a is BinaryOp && a.op is `or` && b is BinaryOp && b.op is `or` ->
            +"${scopedTokenizer(a)} ${op.token} ${scopedTokenizer(b)}"
          // (a1 || a2) && b i.e. need parens around the a
          a is BinaryOp && a.op is `or` -> +"${scopedTokenizer(a)} ${op.token} ${b.token}"
          // a && (b1 || b2) i.e. need parens around the b
          b is BinaryOp && b.op is `or` -> +"${a.token} ${op.token} ${scopedTokenizer(b)}"
          // i.e. don't need parens around a or b
          else -> +"${a.token} ${op.token} ${b.token}"
        }
      a is Any && op is or && b is Any -> +"${a.token} ${op.token} ${b.token}"
      else -> +"${scopedTokenizer(a)} ${op.token} ${scopedTokenizer(b)}"
    }

  // In SQL unary operators will always be in prefix position. Also, if it's just a minus
  // it will be part of the int/long/float/double constant and not a separate unary operator
  // so we don't need to consider that case here.
  val XR.UnaryOp.token get(): Token =
    +"${op.token} (${expr.token})"

  val UnaryOperator.token get(): Token =
    when(this) {
      is OP.minus -> +"-"
      is OP.not -> +"NOT"
    }

  val BinaryOperator.token get(): Token =
    when(this) {
      is OP.`==` -> +"="
      is OP.`!=` -> +"<>"
      is OP.and -> +"AND"
      is OP.or -> +"OR"
      is OP.strPlus -> +"||" // String concat is `||` is most dialects (SQL Server uses + and MySQL only has the `CONCAT` function)
      is OP.minus -> +"-"
      is OP.plus -> +"+"
      is OP.mult -> +"*"
      is OP.gt -> +">"
      is OP.gte -> +">="
      is OP.lt -> +"<"
      is OP.lte -> +"<="
      is OP.div -> +"/"
      is OP.mod -> +"%"

    }


//    case EqualityOperator.`_==`      => stmt"="
//    case EqualityOperator.`_!=`      => stmt"<>"
//    case BooleanOperator.`&&`        => stmt"AND"
//    case BooleanOperator.`||`        => stmt"OR"
//    case StringOperator.`+`          => stmt"||"
//    case StringOperator.`startsWith` => fail("bug: this code should be unreachable")
//    case StringOperator.`split`      => stmt"SPLIT"
//    case NumericOperator.`-`         => stmt"-"
//    case NumericOperator.`+`         => stmt"+"
//    case NumericOperator.`*`         => stmt"*"
//    case NumericOperator.`>`         => stmt">"
//    case NumericOperator.`>=`        => stmt">="
//    case NumericOperator.`<`         => stmt"<"
//    case NumericOperator.`<=`        => stmt"<="
//    case NumericOperator.`/`         => stmt"/"
//    case NumericOperator.`%`         => stmt"%"
//    case SetOperator.`contains`      => stmt"IN"

//  case BinaryOperation(a, EqualityOperator.`_==`, NullValue) => stmt"${scopedTokenizer(a)} IS NULL"
//  case BinaryOperation(NullValue, EqualityOperator.`_==`, b) => stmt"${scopedTokenizer(b)} IS NULL"
//  case BinaryOperation(a, EqualityOperator.`_!=`, NullValue) => stmt"${scopedTokenizer(a)} IS NOT NULL"
//  case BinaryOperation(NullValue, EqualityOperator.`_!=`, b) => stmt"${scopedTokenizer(b)} IS NOT NULL"
///
//  case BinaryOperation(a, StringOperator.`startsWith`, b) =>
//     stmt"${scopedTokenizer(a)} LIKE (${(BinaryOperation(b, StringOperator.`+`, Constant.auto("%")): Ast).token})"
//  case BinaryOperation(a, op @ StringOperator.`split`, b) =>
//     stmt"${op.token}(${scopedTokenizer(a)}, ${scopedTokenizer(b)})"
///
//  case BinaryOperation(a, op @ SetOperator.`contains`, b) => SetContainsToken(scopedTokenizer(b), op.token, a.token)
//  case BinaryOperation(a, op @ `&&`, b) =>
//  (a, b) match {
//    case (BinaryOperation(_, `||`, _), BinaryOperation(_, `||`, _)) =>
//    stmt"${scopedTokenizer(a)} ${op.token} ${scopedTokenizer(b)}"
//    case (BinaryOperation(_, `||`, _), _) => stmt"${scopedTokenizer(a)} ${op.token} ${b.token}"
//    case (_, BinaryOperation(_, `||`, _)) => stmt"${a.token} ${op.token} ${scopedTokenizer(b)}"
//    case _                                => stmt"${a.token} ${op.token} ${b.token}"
//  }
//  case BinaryOperation(a, op @ `||`, b) => stmt"${a.token} ${op.token} ${b.token}"
//  case BinaryOperation(a, op, b)        => stmt"${scopedTokenizer(a)} ${op.token} ${scopedTokenizer(b)}"



  val FromContext.token get(): Token =
    when (this) {
      is TableContext -> +"${entity.token} ${alias.token}"
      is QueryContext -> +"(${query.token})${` AS`} ${alias.token}"
      is ExpressionContext -> +"(${(infix as XR.Expression).token})${` AS`} ${alias.token}"
      is FlatJoinContext -> +"${joinType.token} ${from.token} ON ${on.token}"
    }

  val XR.Free.token get(): Token {
    val pt = parts.map { it.token }
    val pr = params.map { it.token }
    return Statement(pt.intersperseWith(pr))
  }

  val XR.JoinType.token get(): Token =
    when (this) {
      is Left -> +"LEFT JOIN"
      is Inner -> +"INNER JOIN"
      // is RightJoin -> +"RIGHT JOIN"
      // is FullJoin -> +"FULL JOIN"
    }

  val XR.Entity.token get(): Token = tokenizeTable(name)

//  case OrderByCriteria(ast, Asc)            => stmt"${scopedTokenizer(ast)} ASC"
//  case OrderByCriteria(ast, Desc)           => stmt"${scopedTokenizer(ast)} DESC"
//  case OrderByCriteria(ast, AscNullsFirst)  => stmt"${scopedTokenizer(ast)} ASC NULLS FIRST"
//  case OrderByCriteria(ast, DescNullsFirst) => stmt"${scopedTokenizer(ast)} DESC NULLS FIRST"
//  case OrderByCriteria(ast, AscNullsLast)   => stmt"${scopedTokenizer(ast)} ASC NULLS LAST"
//  case OrderByCriteria(ast, DescNullsLast)  => stmt"${scopedTokenizer(ast)} DESC NULLS LAST"

  val OrderByCriteria.token get(): Token =
    when(this.ordering) {
      is Asc -> +"${scopedTokenizer(this.ast)} ASC"
      is Desc -> +"${scopedTokenizer(this.ast)} DESC"
      is AscNullsFirst -> +"${scopedTokenizer(this.ast)} ASC NULLS FIRST"
      is DescNullsFirst -> +"${scopedTokenizer(this.ast)} DESC NULLS FIRST"
      is AscNullsLast -> +"${scopedTokenizer(this.ast)} ASC NULLS LAST"
      is DescNullsLast -> +"${scopedTokenizer(this.ast)} DESC NULLS LAST"
    }

  fun scopedQueryTokenizer(ast: XR.Query) =
    +"(${ast.token})"

  fun scopedTokenizer(ast: XR.Expression) =
    when(ast) {
      is XR.BinaryOp -> +"(${ast.token})"
      is XR.Product -> +"(${ast.token})"
      else -> ast.token
    }

//    ast match {
//      case _: Query           => stmt"(${ast.token})"
//      case _: BinaryOperation => stmt"(${ast.token})"
//      case _: Tuple           => stmt"(${ast.token})"
//      case _                  => ast.token
//    }

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
      is FlattenSqlQuery -> token
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

  val XR.Property.token get(): Token =
    UnnestProperty(this).let { (ast, prefix) ->
      when {
        // This is the typical case. It happens on the outer (i.e. top-level) clause of a multi-level select e.g.
        // SELECT /*this ->*/ foobar... FROM (SELECT foo.bar AS foobar ...)
        // When it's just a top-level select the prefix will be empty
        ast is Ident && ast.visibility == Hidden ->
          joinAlias(prefix).token
        // This happens when the SQL dialect supports some notion of structured-data
        // and we are selecting something from a nested expression
        // SELECT /*this ->*/ (someExpression).otherStuff FROM (....)
        else ->
          +"${scopedTokenizer(ast)}.${(joinAlias(prefix).token)}"
      }
    }

//  TokenizeProperty.unnest(ast) match {
//    // In the rare case that the Ident is invisible, do not show it. See the Ident documentation for more info.
//    case (Ident.Opinionated(_, _, Hidden), prefix) =>
//      stmt"${TokenizeProperty(name, prefix, strategy, renameable)}"
//
//    // The normal case where `Property(Property(Ident("realTable"), embeddedTableAlias), realPropertyAlias)`
//    // becomes `realTable.realPropertyAlias`.
//    case (ast, prefix) =>
//      stmt"${scopedTokenizer(ast)}.${TokenizeProperty(name, prefix, strategy, renameable)}"
//    }
// }

  val XR.Action.token get(): Token =
    when (this) {
      is XR.Insert -> this.token
      is XR.Update -> TODO()
      is XR.Delete -> TODO()
      is XR.OnConflict -> TODO()
      is XR.Returning -> TODO()
    }

  val XR.Insert.token get(): Token = run {
    val query = this.query as? XR.Entity ?: xrError("Insert query must be an entity but found: ${this.query}")
    val (columns, values) = columnsAndValues(assignments)
    +"INSERT INTO ${query.token}${` AS (table)`(alias)} (${columns.mkStmt(", ")}) VALUES (${values.mkStmt(", ")})"

//    case Insert(entity: Entity, assignments) =>
//        val (table, columns, values) = insertInfo(insertEntityTokenizer, entity, assignments)
//        stmt"INSERT INTO $table${` AS [table]`} (${columns
//            .mkStmt(",")}) VALUES ${ValuesClauseToken(stmt"(${values.mkStmt(", ")})")}"
  }

  val List<XR.Assignment>.token get(): Token = run {
    val (columns, values) = columnsAndValues(this)
    (columns zip values).map { (column, value) -> +"${column.token} = ${value.token}" }.mkStmt(", ")
  }

  // TODO possible variable-shadowing issues might require beta-reducing out the alias of the inner query first.
  //      Do that instead of creating an ExternalIdent like was done in Quill #1509.
  val XR.Returning.token get(): Token =
    when (output) {
      // In Postgres-style RETURNING clause the RETURNING is always the last thing to be used so we can
      // use the action renderes first. In SQL-server that uses an OUTPUT clause this is not the case
      // and we need to repeat some logic here.
      is XR.Returning.Kind.Expression ->
        +"${action.token} RETURNING ${scopedTokenizer(output.expr)}"
      // This is when an API like insert(...).returningColumns(...) is used.
      // In this case the PrepareStatement.getGeneratedKeys() should be used but there should
      // be no specific RETURNING clause in the SQL.
      is XR.Returning.Kind.Keys ->
        action.token
    }

  val XR.Delete.token get(): Token = run {
    fun deleteBase() = +"DELETE FROM ${query.token}${` AS (table)`(alias)}"
    when {
      query is XR.Filter && query.head is XR.Entity ->
        deleteBase()
      query is XR.Entity ->
        +"${deleteBase()} WHERE ${query.token}"
      else ->
        xrError("Invalid query-clause in a Delete. It can only be a XR Filter or Entity but was:\n${query.showRaw()}")
    }
  }

  val XR.Update.token get(): Token = run {
    fun updateBase() = +"UPDATE ${query.token}${` AS (table)`(alias)} SET ${assignments.token}"
    when {
      query is XR.Filter && query.head is XR.Entity ->
        updateBase()
      query is XR.Entity ->
        +"${updateBase()} WHERE ${query.token}"
      else ->
        xrError("Invalid query-clause in an Update. It can only be a XR Filter or Entity but was:\n${query.showRaw()}")
    }
  }

  // AS [table] specifically for actions (where for some dialects it shouldn't even be there)
  fun ` AS (table)`(alias: XR.Ident): Token =
    if (alias.isThisRef()) emptyStatement
    else
      when (useActionTableAliasAs) {
        ActionTableAliasBehavior.UseAs -> +" AS ${alias.token}"
        ActionTableAliasBehavior.SkipAs -> +" ${alias.token}"
        ActionTableAliasBehavior.Hide -> emptyStatement
      }

  // Scala
//  private[getquill] def ` AS [table]`(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy) =
//  useActionTableAliasAs match {
//    case ActionTableAliasBehavior.UseAs =>
//    actionAlias.map(alias => stmt" AS ${alias.token}").getOrElse(emptyStatement)
//    case ActionTableAliasBehavior.SkipAs => actionAlias.map(alias => stmt" ${alias.token}").getOrElse(emptyStatement)
//    case ActionTableAliasBehavior.Hide   => emptyStatement
//  }


  fun columnsAndValues(assignments: List<XR.Assignment>): Pair<List<Token>, List<Token>> {
    val columns = assignments.map { assignment ->
      when (val property = assignment.property) {
        is XR.Property -> tokenizeColumn(property)
        else -> xrError("Invalid assignment value of ${assignment}. Must be a Property object.")
      }
    }
    val values = assignments.map { assignment -> scopedTokenizer(assignment.value) }
    return columns to values
  }

  fun tokenizeColumn(property: XR.Property): Token =
    when {
      property.of is XR.Ident && property.of.isThisRef() -> property.name.token
      property.of is XR.Property -> "${tokenizeColumn(property.of)}.${property.name}".token
      else -> xrError("Invalid column setter: ${property.showRaw()}")
    }

// Scala
//  private[getquill] def columnsAndValues(
//  assignments: List[Assignment]
//  )(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy) = {
//    val columns =
//      assignments.map(assignment =>
//    assignment.property match {
//      case Property.Opinionated(_, key, renameable, visibility) => tokenizeColumn(strategy, key, renameable).token
//      case _                                                    => fail(s"Invalid assignment value of ${assignment}. Must be a Property object.")
//    }
//    )
//    val values = assignments.map(assignment => scopedTokenizer(assignment.value))
//    (columns, values)
//  }



  // Scala
//  private def insertInfo(
//  insertEntityTokenizer: Tokenizer[Entity],
//  entity: Entity,
//  assignments: List[Assignment]
//  )(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy) = {
//    val table             = insertEntityTokenizer.token(entity)
//    val (columns, values) = columnsAndValues(assignments)
//    (table, columns, values)
//  }

}
