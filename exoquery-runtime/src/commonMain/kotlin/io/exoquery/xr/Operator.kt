package io.exoquery.xr

import kotlinx.serialization.Serializable

@Serializable
sealed interface Operator { val symbol: String }

@Serializable sealed interface UnaryOperator: Operator
@Serializable sealed interface PrefixUnaryOperator: UnaryOperator
@Serializable sealed interface PostfixUnaryOperator: UnaryOperator
@Serializable sealed interface BinaryOperator: Operator

@Serializable sealed interface YieldsBool

@Serializable
sealed interface EqualityOperator: BinaryOperator {
  @Serializable data object `==`: EqualityOperator, YieldsBool { override val symbol = "=="; override fun toString() = symbol }
  @Serializable data object `!=`: EqualityOperator, YieldsBool { override val symbol = "!="; override fun toString() = symbol }
}

object BooleanOperator {
  @Serializable data object not : PrefixUnaryOperator, YieldsBool { override val symbol = "!"}
  @Serializable data object and : BinaryOperator, YieldsBool { override val symbol = "&&"}
  @Serializable data object or : BinaryOperator, YieldsBool { override val symbol = "||" }
}

object NumericOperator {
  @Serializable data object plus : BinaryOperator { override val symbol = "+" }
  @Serializable data object minus : BinaryOperator, PrefixUnaryOperator { override val symbol = "-" }
  @Serializable data object mult : BinaryOperator { override val symbol = "*" }
  @Serializable data object gt : BinaryOperator, YieldsBool { override val symbol = ">" }
  @Serializable data object gte: BinaryOperator, YieldsBool { override val symbol = ">=" }
  @Serializable data object lt : BinaryOperator, YieldsBool { override val symbol = "<" }
  @Serializable data object lte: BinaryOperator, YieldsBool { override val symbol = "<=" }
  @Serializable data object div : BinaryOperator { override val symbol = "/" }
  @Serializable data object mod : BinaryOperator { override val symbol = "%" }
}

// TODO remove these and replace with XR.MethodCall/GlobalCall
object StringOperator {
  @Serializable data object `+`          : BinaryOperator { override val symbol = "+" }
// TODO implement these as GlobalCall and MethodCall
//  @Serializable data object `startsWith` : BinaryOperator, YieldsBool { override val symbol = "startsWith" }
//  @Serializable data object `split`      : BinaryOperator { override val symbol = "split" }
//  @Serializable data object `toUpperCase`: PostfixUnaryOperator { override val symbol = "toUpperCase" }
//  @Serializable data object `toLowerCase`: PostfixUnaryOperator { override val symbol = "toLowerCase" }
//  @Serializable data object `toLong`     : PostfixUnaryOperator { override val symbol = "toLong" }
//  @Serializable data object `toInt`      : PostfixUnaryOperator { override val symbol = "toInt" }
}


object SetOperator {
  @Serializable data object `contains`: BinaryOperator, YieldsBool { override val symbol = "contains" }
  @Serializable data object `nonEmpty`: PostfixUnaryOperator, YieldsBool { override val symbol = "nonEmpty" }
  @Serializable data object `isEmpty` : PostfixUnaryOperator, YieldsBool { override val symbol = "isEmpty" }
}

// TODO remove these and replace with XR.GlobalCall and custom aggregation methods in the DSL
@Serializable
sealed interface AggregationOperator: Operator {
  @Serializable data object `min` : AggregationOperator { override val symbol = "min" }
  @Serializable data object `max` : AggregationOperator { override val symbol = "max" }
  @Serializable data object `avg` : AggregationOperator { override val symbol = "avg" }
  @Serializable data object `sum` : AggregationOperator { override val symbol = "sum" }
  @Serializable data object `size`: AggregationOperator { override val symbol = "size" }
}
