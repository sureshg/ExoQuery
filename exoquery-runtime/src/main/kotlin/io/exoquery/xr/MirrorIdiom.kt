package io.exoquery.xr

import io.exoquery.terpal.Interpolator
import io.exoquery.xr.XR.*

class MirrorIdiom {

  val XR.token get(): String =
    when(this) {
      is XR.Expression -> token
      is Query -> token
      is Branch -> token
      is Variable -> token
    }

  val Ident.token get(): String = name
  val IdentOrigin.token get(): String = """Ido(${name}->"${runtimeName.value}")"""
  val Operator.token get(): String = symbol
  val Variable.token get(): String = "val ${name.name} = ${rhs.token}"

  val XR.tokenScoped get(): String =
    when (this) {
      is XR.Labels.Function -> "(${this.token})"
      is BinaryOp -> "(${this.token})"
      is When -> "(${this.token})"
      else -> this.token
    }

  val Const.token get(): String =
    when(this) {
      is Const.String -> "\"$value\""
      is Const.Boolean -> value.toString()
      is Const.Int -> value.toString()
      is Const.Long -> value.toString()
      is Const.Float -> value.toString()
      is Const.Double -> value.toString()
      is Const.Null -> "null"
      is Const.Char -> "'$value'"
      is Const.Byte -> value.toString()
      is Const.Short -> value.toString()
    }

  fun <T> List<T>.token(elemTokenizer: (T) -> String): String = this.map(elemTokenizer).joinToString(",")

  val Expression.token get(): String =
    when(this) {
      is UnaryOp ->
        when (op) {
          is PrefixUnaryOperator -> "${op.token}${expr.token}"
          is PostfixUnaryOperator -> "${expr.token}.${op.token}"
        }
      is BinaryOp ->
        when (op) {
          // Usually need special handling for this because could have multiple values in B, doesn't mater here
          is SetOperator.`contains` -> "${a.tokenScoped}.${token}(${b})"
          else -> "${a.tokenScoped} ${op.token} ${b.tokenScoped}"
        }
      is When ->
        if (branches.size == 1) {
          val b = branches.first()
          "if (${b.cond.token}) ${b.then.token} else ${orElse.token}"
        } else {
          "when { ${branches.map { it.token }.joinToString("; ")}; else -> ${orElse.token} }"
        }
      is Block -> "block { ${stmts.map { it.token }.joinToString("; ")}; ${output.token} }"
      is Function1 -> "(${params.token { it.token }}) -> ${body.token}"
      is FunctionN -> "(${params.token { it.token }}) -> ${body.token}"
      is FunctionApply -> "${function.tokenScoped}.apply(${args.token { it.token }})"
      is Product -> "${name}(${fields.map { (k, v) -> "${k}: ${v.token}" }.joinToString(", ")})"
      is Property -> "${of.tokenScoped}.${name}"
      is Aggregation -> "${expr.token}.${op.token}"
      is MethodCall -> "Call(${head.token}.${name.name}(${args.token { it.token }}))"
      is GlobalCall -> "GCall(${name.name}(${args.token { it.token }}))"
      is ValueOf -> "${head.token}.value"
      is Ident -> token
      is IdentOrigin -> token
      is Const -> token
      is Marker -> token
      is Infix -> token
    }

  val Infix.token get(): String {
    fun tokenParam(xr: XR) =
      when (xr) {
        is Ident -> "$" + xr.name
        else -> "$" + "{${xr.token}}"
      }

    val parts = this.parts
    val params = params.map { tokenParam(it) }
    val body = Interpolator.interlace(parts, params, {""}, { it }, { a, b -> a + b })
    return """infix"${body}""""
  }

  val Branch.token get(): String = "${cond} -> ${then}"

  val Marker.token get(): String {
    val elem = this.expr
    val elemPrint = if (elem == null) "" else "->${elem.token}"
    return "MARKER(${name}${elemPrint})"
  }

  val Ordering.token get(): String =
    when(this) {
      is Ordering.TupleOrdering -> "Ord(${elems.token { it.token }})"
      is Ordering.Asc -> "Ordering.Asc"
      is Ordering.Desc -> "Ordering.Desc"
      is Ordering.AscNullsFirst -> "Ordering.AscNullsFirst"
      is Ordering.DescNullsFirst -> "Ordering.DescNullsFirst"
      is Ordering.AscNullsLast -> "Ordering.AscNullsLast"
      is Ordering.DescNullsLast -> "Ordering.DescNullsLast"
    }

  val AggregationOperator.token get(): String =
    when(this) {
      is AggregationOperator.`min` -> "min"
      is AggregationOperator.`max` -> "max"
      is AggregationOperator.`avg` -> "avg"
      is AggregationOperator.`sum` -> "sum"
      is AggregationOperator.`size` -> "size"
    }

  val JoinType.token get(): String =
    when(this) {
      is JoinType.Inner -> "join"
      is JoinType.Left -> "leftJoin"
    }

  val Query.token get(): String =
    when(this) {
      is Entity -> """query("$name")"""
      is Filter -> "${head.token}.filter { ${id.token} -> ${body.token} }"
      is XR.Map -> "${head.token}.map { ${id.token} -> ${body.token} }"
      is FlatMap -> "${head.token}.flatMap { ${id.token} -> ${body.token} }"
      is ConcatMap -> "${head.token}.concatMap { ${id.token} -> ${body.token} }"
      is SortBy -> "${head.token}.sortedBy { ${id.token} -> ${criteria.token} }(${ordering.token})"
      is GroupByMap -> "${head.token}.groupByMap { ${byAlias.token} -> ${byBody.token} } { ${mapAlias.token} -> ${mapBody.token} }"
      is Take -> "${head.token}.take(${num.token})"
      is Drop -> "${head.token}.drop(${num.token})"
      is Union -> "${a.token}.union(${b.token})"
      is UnionAll -> "${a.token}.unionAll(${b.token})"
      is FlatJoin -> "${head.token}.${joinType.token} { ${id.token} -> ${on.token} }"
      is FlatGroupBy -> "flatGroupBy { ${by.token} }"
      is FlatSortBy -> "flatSortBy { ${by.token} }(${ordering.token})"
      is FlatFilter -> "flatFilter { ${by.token} }"
      is Distinct -> "${head.token}.distinct"
      is DistinctOn -> "${head.token}.distinctOn { ${id.token} -> ${by.token} }"
      is Nested -> "${head.token}.nested"
      is Marker -> token
      is Infix -> token
      is RuntimeQueryBind -> """runtimeQuery(${id.value.takeLast(4)})"""
    }

}
