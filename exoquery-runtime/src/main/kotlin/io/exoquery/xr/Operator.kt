package io.exoquery.xr

sealed interface Operator { val symbol: String }

sealed interface UnaryOperator: Operator
sealed interface PrefixUnaryOperator: UnaryOperator
sealed interface PostfixUnaryOperator: UnaryOperator
sealed interface BinaryOperator: Operator

sealed interface YieldsBool

sealed interface EqualityOperator: BinaryOperator {
  /*data*/ object `==`: EqualityOperator, YieldsBool { override val symbol = "=="; override fun toString() = symbol }
  /*data*/ object `!=`: EqualityOperator, YieldsBool { override val symbol = "!="; override fun toString() = symbol }
}

object BooleanOperator {
  /*data*/ object not : PrefixUnaryOperator, YieldsBool { override val symbol = "!"}
  /*data*/ object and : BinaryOperator, YieldsBool { override val symbol = "&&"}
  /*data*/ object or : BinaryOperator, YieldsBool { override val symbol = "||" }
}

object NumericOperator {
  /*data*/ object minus : BinaryOperator, PrefixUnaryOperator { override val symbol = "-" }
  /*data*/ object plus : BinaryOperator { override val symbol = "+" }
  /*data*/ object mult : BinaryOperator { override val symbol = "*" }
  /*data*/ object gt : BinaryOperator, YieldsBool { override val symbol = ">" }
  /*data*/ object gte: BinaryOperator, YieldsBool { override val symbol = ">=" }
  /*data*/ object lt : BinaryOperator, YieldsBool { override val symbol = "<" }
  /*data*/ object lte: BinaryOperator, YieldsBool { override val symbol = "<=" }
  /*data*/ object div : BinaryOperator { override val symbol = "/" }
  /*data*/ object mod : BinaryOperator { override val symbol = "%" }
}

object StringOperator {
  /*data*/ object `+`          : BinaryOperator { override val symbol = "+" }
  /*data*/ object `startsWith` : BinaryOperator, YieldsBool { override val symbol = "startsWith" }
  /*data*/ object `split`      : BinaryOperator { override val symbol = "split" }
  /*data*/ object `toUpperCase`: PostfixUnaryOperator { override val symbol = "toUpperCase" }
  /*data*/ object `toLowerCase`: PostfixUnaryOperator { override val symbol = "toLowerCase" }
  /*data*/ object `toLong`     : PostfixUnaryOperator { override val symbol = "toLong" }
  /*data*/ object `toInt`      : PostfixUnaryOperator { override val symbol = "toInt" }
}



object SetOperator {
  /*data*/ object `contains`: BinaryOperator, YieldsBool { override val symbol = "contains" }
  /*data*/ object `nonEmpty`: PostfixUnaryOperator, YieldsBool { override val symbol = "nonEmpty" }
  /*data*/ object `isEmpty` : PostfixUnaryOperator, YieldsBool { override val symbol = "isEmpty" }
}

sealed interface AggregationOperator: Operator {
  /*data*/ object `min` : AggregationOperator { override val symbol = "min" }
  /*data*/ object `max` : AggregationOperator { override val symbol = "max" }
  /*data*/ object `avg` : AggregationOperator { override val symbol = "avg" }
  /*data*/ object `sum` : AggregationOperator { override val symbol = "sum" }
  /*data*/ object `size`: AggregationOperator { override val symbol = "size" }
}
