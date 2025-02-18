package io.exoquery.sql

import io.exoquery.sql.mkStmt
import io.exoquery.util.interleaveWith
import io.exoquery.util.stmt
import io.exoquery.xr.AggregationOperator
import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.OP
import io.exoquery.xr.PostfixUnaryOperator
import io.exoquery.xr.PrefixUnaryOperator
import io.exoquery.xr.SX
import io.exoquery.xr.SelectClause
import io.exoquery.xr.XR
import io.exoquery.xr.id


fun String.uncapitalize() = replaceFirstChar { it.lowercaseChar() }

class MirrorIdiom(val renderOpts: RenderOptions = RenderOptions()) {
  data class RenderOptions(val showProductConstructorFields: Boolean = true, val showMethodCallSuffixes: Boolean = true, val showFullBids: Boolean = false)

  val <T: XR> T.tokenScoped: Token get() =
    when (this) {
      is XR.BinaryOp -> stmt("(${this.token})")
      else -> this.token
    }

  fun <T: XR> List<T>.token(elementTokenizer: (T) -> Token): Token =
    if (this.isEmpty())
      stmt("emptyList()")
    else
      stmt("listOf(${this.map(elementTokenizer).mkStmt()})")


  val XR.token: Token get() =
    when (this) {
      is XR.Query -> this.token
      is XR.Expression -> this.token
      is XR.Branch -> this.token
      is XR.Variable -> this.token
    }

  val XR.Variable.token: Token get() = stmt("val ${name.token} = ${this.rhs.token}")
  val XR.Branch.token: Token get() = stmt("${cond.token} -> ${then.token}")

  val XR.Ident.token: Token get() = name.token

  val XR.JoinType.token: Token get() = simpleName.token

  val BinaryOperator.token: Token get() = symbol.token
  val AggregationOperator.token: Token get() = symbol.token

  // stmt"${scopedTokenizer(function)}.apply(${values.token})"
  val XR.FunctionApply.token: Token get() = stmt("${function.tokenScoped}.apply(${args.map { it.token }.mkStmt(", ")})")
  val XR.FunctionN.token: Token get() = stmt("{ ${params.map { it.name.token }.mkStmt(", ")} -> ${body.token} }")
  val XR.Const.token: Token get() =
    when (this) {
      is XR.ConstType<*> -> stmt("${value.toString().token}")
      is XR.Const.Null -> stmt("null")
    }

  val XR.U.QueryOrExpression.token: Token get() =
    when (this) {
      is XR.Query -> this.token
      is XR.Expression -> this.token
    }

  fun productKV(k: String, v: XR.Expression): Token =
    if (renderOpts.showProductConstructorFields)
      stmt("${k.token} = ${v.token}")
    else
      stmt("${v.token}")

  fun XR.MethodCall.suffix() =
    if (renderOpts.showMethodCallSuffixes)
      stmt("_MC")
    else
      stmt("")

  fun XR.GlobalCall.suffix() =
    if (renderOpts.showMethodCallSuffixes)
      stmt("_GC")
    else
      stmt("")

  val XR.Ordering.token: Token get() = (this::class.simpleName ?: "UNKNOWN").token

  val XR.GlobalCall.token: Token get() =
    stmt("${name.name.token}${suffix().token}(${args.map { it.token }.mkStmt(", ")})")
  val XR.MethodCall.token: Token get() =
    stmt("${head.tokenScoped}.${name.token}${suffix().token}(${args.map { it.token }.mkStmt(", ")})")

  val XR.Expression.token: Token get() =
    when (this) {
      is XR.BinaryOp -> stmt("${a.token} ${op.token} ${b.token}")

      is XR.UnaryOp ->
        when (op) {
          is PrefixUnaryOperator -> stmt("${op.symbol.token}${expr.token}")
          // TODO these are not supposed to be on Expr, they're supposed to be on Query. Replace them with MethodCall/GlobalCall
          is OP.isEmpty -> stmt("${expr.token}.isEmpty")
          is OP.nonEmpty -> stmt("${expr.token}.nonEmpty")
        }

      is XR.Block -> stmt("{ ${stmts.map { it.token }.mkStmt("; ").token}; ${output.token} }")

      is XR.FunctionN -> this.token
      is XR.GlobalCall -> this.token
      is XR.MethodCall -> this.token
      is XR.When ->
        if (this.branches.size == 1)
          // for only one branch, make it a if-statement
          stmt("if (${this.branches.first().cond.token}) ${this.branches.first().then.token} else ${orElse.token}")
        else
          stmt("when { ${this.branches.map { it.token }.mkStmt("; ").token}; else ->- ${orElse.token} }")

      is XR.FunctionApply -> this.token
      is XR.Ident -> this.token
      is XR.Infix -> this.token
      is XR.Const -> this.token
      // scala: stmt"${name.token}(${values.map { case (k, v) -> s"${k.token}: ${v.token}" }.mkString(", ").token})"
      is XR.Product ->
        // TODO should have a 'showFieldNames' setting to disable display of field-names in products
        stmt("${name.token}(${fields.map { (k, v) -> productKV(k, v) }.mkStmt()})")
      is XR.Property ->
        stmt("${of.tokenScoped}.${name.token}")
      is XR.QueryToExpr ->
        stmt("${head.token}.toExpr")
      is XR.TagForParam ->
        stmt("""TagP("${id.value.trimId()}")""")
      is XR.TagForSqlExpression ->
        stmt("""TagE("${id.value.trimId()}")""")
    }


  val XR.Query.token: Token get() =
    when(this) {
      is XR.Entity ->
        stmt("${"Table".token}(${name.token})")
      is XR.Filter ->
        stmt("${head.token}.filter { ${id.token} -> ${body.token} }")
      is XR.Map ->
        stmt("${head.token}.map { ${id.token} -> ${body.token} }")
      is XR.FlatMap ->
        stmt("${head.token}.flatMap { ${id.token} -> ${body.token} }")
      is XR.ConcatMap ->
        stmt("${head.token}.concatMap { ${id.token} -> ${body.token} }")
      is XR.SortBy ->
        stmt("${head.token}.sortBy(${ordering.token}) { ${id.token} -> ${criteria.token} }")
      is XR.Take ->
        stmt("${head.token}.take(${num.token})")
      is XR.Drop ->
        stmt("${head.token}.drop(${num.token})")
      is XR.Union ->
        stmt("${a.token}.union(${b.token})")
      is XR.UnionAll ->
        stmt("${a.token}.unionAll(${b.token})")
      is XR.FlatJoin ->
        stmt("${head.token}.${joinType.token} { ${id.token} -> ${on.token} }")
      is XR.FlatFilter ->
        stmt("where(${by.token})")
      is XR.FlatGroupBy ->
        stmt("groupBy(${by.token})")
      is XR.FlatSortBy ->
        stmt("sortBy(${by.token})(${ordering.token})")
      is XR.Distinct ->
        stmt("${head.token}.distinct")
      is XR.DistinctOn ->
        stmt("${head.token}.distinctOn { ${id.token} -> ${by.token} }")
      is XR.Nested ->
        stmt("${head.token}.nested")
      is XR.CustomQueryRef -> this.token
      is XR.ExprToQuery -> stmt("${head.tokenScoped}.toQuery")
      is XR.FunctionApply -> this.token
      is XR.Ident -> this.token
      is XR.Infix -> this.token
      is XR.TagForSqlQuery -> stmt("""TagQ("${id.value.trimId()}")""")
      is XR.GlobalCall -> this.token
      is XR.MethodCall -> this.token
    }

  private fun String.trimId() =
    if (renderOpts.showFullBids)
      this
    else
      this.takeLast(5)

// Scala:
//  implicit final def queryTokenizer(implicit externalTokenizer: Tokenizer[External]): Tokenizer[AstQuery] =
//    Tokenizer[AstQuery] {
//
//      case Entity.Opinionated(name, Nil, _, renameable) ->
//        stmt"${tokenizeName("querySchema", renameable).token}(${s""""$name"""".token})"
//
//      case Entity.Opinionated(name, prop, _, renameable) ->
//        val properties = prop.map(p -> stmt"""_.${p.path.mkStmt(".")} -> "${p.alias.token}"""")
//        stmt"${tokenizeName("querySchema", renameable).token}(${s""""$name"""".token}, ${properties.token})"
//
//      case Filter(source, alias, body) ->
//        stmt"${source.token}.filter(${alias.token} -> ${body.token})"
//
//      case Map(source, alias, body) ->
//        stmt"${source.token}.map(${alias.token} -> ${body.token})"
//
//      case FlatMap(source, alias, body) ->
//        stmt"${source.token}.flatMap(${alias.token} -> ${body.token})"
//
//      case ConcatMap(source, alias, body) ->
//        stmt"${source.token}.concatMap(${alias.token} -> ${body.token})"
//
//      case SortBy(source, alias, body, ordering) ->
//        stmt"${source.token}.sortBy(${alias.token} -> ${body.token})(${ordering.token})"
//
//      case GroupBy(source, alias, body) ->
//        stmt"${source.token}.groupBy(${alias.token} -> ${body.token})"
//
//      case GroupByMap(source, byAlias, byBody, mapAlias, mapBody) ->
//        stmt"${source.token}.groupByMap(${byAlias.token} -> ${byBody.token})(${mapAlias.token} -> ${mapBody.token})"
//
//      case Aggregation(op, ast) ->
//        stmt"${scopedTokenizer(ast)}.${op.token}"
//
//      case Take(source, n) ->
//        stmt"${source.token}.take(${n.token})"
//
//      case Drop(source, n) ->
//        stmt"${source.token}.drop(${n.token})"
//
//      case Union(a, b) ->
//        stmt"${a.token}.union(${b.token})"
//
//      case UnionAll(a, b) ->
//        stmt"${a.token}.unionAll(${b.token})"
//
//      case Join(t, a, b, iA, iB, on) ->
//        stmt"${a.token}.${t.token}(${b.token}).on((${iA.token}, ${iB.token}) -> ${on.token})"
//
//      case FlatJoin(t, a, iA, on) ->
//        stmt"${a.token}.${t.token}((${iA.token}) -> ${on.token})"
//
//      case Distinct(a) ->
//        stmt"${a.token}.distinct"
//
//      case DistinctOn(source, alias, body) ->
//        stmt"${source.token}.distinctOn(${alias.token} -> ${body.token})"
//
//      case Nested(a) ->
//        stmt"${a.token}.nested"
//    }


  val XR.Infix.token: Token get() = run {
    val dol = ('$' + "").token
    fun tokenParam(ast: XR): Token =
      when (ast) {
        is XR.Ident -> stmt("${dol}${ast.token}")
        else -> stmt("${dol}{${ast.token}}")
      }
    val pt = parts.map { it.token }
    val pr = params.map { tokenParam(it) }
    val body = pt.interleaveWith(pr)
    stmt("sql(\"${body.token{ it }}\")")
  }


// Scala:
//  implicit final def infixTokenizer(implicit externalTokenizer: Tokenizer[External]): Tokenizer[Infix] =
//  Tokenizer[Infix] { case Infix(parts, params, _, _, _) ->
//    def tokenParam(ast: Ast) =
//    ast match {
//      case ast: Ident -> stmt"$$${ast.token}"
//      case _          -> stmt"$${${ast.token}}"
//    }
//
//    val pt   = parts.map(_.token)
//    val pr   = params.map(tokenParam)
//    val body = Statement(Interleave(pt, pr))
//    stmt"""sql"${body.token}""""
//  }



  // ----------------- Tokenizer for SelectClause and its fields -----------------
  val XR.CustomQueryRef.token: Token get() =
    when (this.customQuery) {
      is SelectClause -> customQuery.token
      else -> stmt("CustomQueryRef(${this.customQuery.toString()})")
    }

  val SelectClause.token: Token get() =
    stmt("select { ${allComponents().map { it.token }.mkStmt("; ")} }")

  val SX.token: Token get() =
    when (this) {
      is SX.From -> stmt("val ${variable.token} = from(${xr.token})")
      is SX.Join -> stmt("val ${variable.token} = ${joinType.simpleName.token}(${onQuery.token}) { ${condition.token} }")
      is SX.GroupBy -> stmt("groupBy(${grouping.token})")
      is SX.SortBy -> stmt("sortBy(${ordering.token})(${sorting.token})")
      is SX.Where -> stmt("where(${condition.token})")
    }

}
