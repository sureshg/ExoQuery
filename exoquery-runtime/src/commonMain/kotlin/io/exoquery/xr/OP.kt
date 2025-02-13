package io.exoquery.xr

import kotlinx.serialization.Serializable



@Serializable sealed interface UnaryOperator: OP
@Serializable sealed interface PrefixUnaryOperator: UnaryOperator
@Serializable sealed interface PostfixUnaryOperator: UnaryOperator
@Serializable sealed interface BinaryOperator: OP
@Serializable sealed interface YieldsBool

@Serializable sealed interface SetOperator: OP
@Serializable sealed interface AggregationOperator: OP
@Serializable sealed interface StringOperator: OP
@Serializable sealed interface NumericOperator: OP
@Serializable sealed interface BooleanOperator: OP
@Serializable sealed interface EqualityOperator: OP

@Serializable
sealed interface OP {
  val symbol: String

  @Serializable data object `==`: BinaryOperator, EqualityOperator, YieldsBool { override val symbol = "=="; override fun toString() = symbol }
  @Serializable data object `!=`: BinaryOperator, EqualityOperator, YieldsBool { override val symbol = "!="; override fun toString() = symbol }

  @Serializable data object not : BooleanOperator, PrefixUnaryOperator, YieldsBool { override val symbol = "!"}
  @Serializable data object and : BooleanOperator, BinaryOperator, YieldsBool { override val symbol = "&&"}
  @Serializable data object or : BooleanOperator, BinaryOperator, YieldsBool { override val symbol = "||" }


  @Serializable data object plus : NumericOperator, BinaryOperator { override val symbol = "+" }
  @Serializable data object minus : NumericOperator, BinaryOperator, PrefixUnaryOperator { override val symbol = "-" }
  @Serializable data object mult : NumericOperator, BinaryOperator { override val symbol = "*" }
  @Serializable data object gt : NumericOperator, BinaryOperator, YieldsBool { override val symbol = ">" }
  @Serializable data object gte: NumericOperator, BinaryOperator, YieldsBool { override val symbol = ">=" }
  @Serializable data object lt : NumericOperator, BinaryOperator, YieldsBool { override val symbol = "<" }
  @Serializable data object lte: NumericOperator, BinaryOperator, YieldsBool { override val symbol = "<=" }
  @Serializable data object div : NumericOperator, BinaryOperator { override val symbol = "/" }
  @Serializable data object mod : NumericOperator, BinaryOperator { override val symbol = "%" }

  @Serializable data object strPlus          : StringOperator, BinaryOperator { override val symbol = "+" }
// TODO implement these as GlobalCall and MethodCall
//  @Serializable data object `startsWith` : BinaryOperator, YieldsBool { override val symbol = "startsWith" }
//  @Serializable data object `split`      : BinaryOperator { override val symbol = "split" }
//  @Serializable data object `toUpperCase`: PostfixUnaryOperator { override val symbol = "toUpperCase" }
//  @Serializable data object `toLowerCase`: PostfixUnaryOperator { override val symbol = "toLowerCase" }
//  @Serializable data object `toLong`     : PostfixUnaryOperator { override val symbol = "toLong" }
//  @Serializable data object `toInt`      : PostfixUnaryOperator { override val symbol = "toInt" }

  @Serializable data object `contains`: SetOperator, BinaryOperator, YieldsBool { override val symbol = "contains" }
  @Serializable data object `nonEmpty`: SetOperator, PostfixUnaryOperator, YieldsBool { override val symbol = "nonEmpty" }
  @Serializable data object `isEmpty` : SetOperator, PostfixUnaryOperator, YieldsBool { override val symbol = "isEmpty" }

  @Serializable data object `min` : AggregationOperator { override val symbol = "min" }
  @Serializable data object `max` : AggregationOperator { override val symbol = "max" }
  @Serializable data object `avg` : AggregationOperator { override val symbol = "avg" }
  @Serializable data object `sum` : AggregationOperator { override val symbol = "sum" }
  @Serializable data object `size`: AggregationOperator { override val symbol = "size" }
}
