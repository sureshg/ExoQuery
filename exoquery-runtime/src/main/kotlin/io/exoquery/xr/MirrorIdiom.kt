package io.exoquery.xr

import io.exoquery.xr.XR.*

object MirrorIdiom {

  val XR.token get(): String =
    when(this) {
      is XR.Expression -> token
      is Query -> token
      is XR.Function -> token
      is Block -> token
      is Branch -> token
      is Variable -> token
    }

  val Ident.token get(): String = name
  val Operator.token get(): String = symbol

  val XR.Function.token get(): String =
    "(${params.token { it.token }}) -> ${body.token}"

  val XR.tokenScoped get(): String =
    when (this) {
      is XR.Function -> "(${this.token})"
      is BinaryOp -> "(${this.token})"
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
          "if (${b.cond}) ${b.then} else ${orElse}"
        } else {
          "when { ${branches.map { it.token }.joinToString("; ")}; else -> ${orElse} }"
        }
      is FunctionApply -> "${function.tokenScoped}.apply(${args.token { it.token }})"
      is Product -> "${name}(${fields.map { (k, v) -> "${k}: ${v.token}" }.joinToString(", ")})"
      is Property -> "${of.tokenScoped}.${name}"
      is Ident -> token
      is Const -> token
      is Marker -> token
    }

  val Branch.token get(): String = "${cond} -> ${then}"

  val Marker.token get(): String = "MARKER($name)"

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
      is Entity -> """"$name""""
      is Filter -> "${a.token}.filter(${ident.token} => ${b.token})"
      is XR.Map -> "${a.token}.map(${ident.token} => ${b.token})"
      is FlatMap -> "${a.token}.flatMap(${ident.token} => ${b.token})"
      is ConcatMap -> "${a.token}.concatMap(${ident.token} => ${b.token})"
      is SortBy -> "${query.token}.sortBy(${alias.token} => ${criteria.token})(${ordering.token})"
      is GroupByMap -> "${query.token}.groupByMap(${byAlias.token} => ${byBody.token})(${mapAlias.token} => ${mapBody.token})"
      is Aggregation -> "${body.token}.${operator.token}"
      is Take -> "${query.token}.take(${num.token})"
      is Drop -> "${query.token}.drop(${num.token})"
      is Union -> "${a.token}.union(${b.token})"
      is UnionAll -> "${a.token}.unionAll(${b.token})"
      is FlatJoin -> "${a.token}.${joinType.token}(${aliasA.token} => ${on.token})"
      is Distinct -> "${query.token}.distinct"
      is DistinctOn -> "${query.token}.distinctOn(${alias.token} => ${by.token})"
      is Nested -> "${query.token}.nested"
      is Marker -> "(MARKER)"
    }

}
