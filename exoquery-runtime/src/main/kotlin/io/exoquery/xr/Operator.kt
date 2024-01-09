package io.exoquery.xr

sealed interface Operator

sealed interface UnaryOperator: Operator
sealed interface PrefixUnaryOperator: UnaryOperator
sealed interface PostfixUnaryOperator: UnaryOperator
sealed interface BinaryOperator: Operator

sealed interface YieldsBool

sealed interface EqualityOperator: BinaryOperator {
  /*data*/ object `==`: EqualityOperator, YieldsBool
  /*data*/ object `!=`: EqualityOperator, YieldsBool
}

object BooleanOperator {
  /*data*/ object not : PrefixUnaryOperator, YieldsBool
  /*data*/ object and : BinaryOperator, YieldsBool
  /*data*/ object or : BinaryOperator, YieldsBool
}

object NumericOperator {
  /*data*/ object minus : BinaryOperator, PrefixUnaryOperator
  /*data*/ object plus : BinaryOperator
  /*data*/ object mult : BinaryOperator
  /*data*/ object gt : BinaryOperator, YieldsBool
  /*data*/ object gte: BinaryOperator, YieldsBool
  /*data*/ object lt : BinaryOperator, YieldsBool
  /*data*/ object lte: BinaryOperator, YieldsBool
  /*data*/ object div : BinaryOperator
  /*data*/ object mod : BinaryOperator
}

object StringOperator {
  /*data*/ object `+`          : BinaryOperator
  /*data*/ object `startsWith` : BinaryOperator, YieldsBool
  /*data*/ object `split`      : BinaryOperator
  /*data*/ object `toUpperCase`: PostfixUnaryOperator
  /*data*/ object `toLowerCase`: PostfixUnaryOperator
  /*data*/ object `toLong`     : PostfixUnaryOperator
  /*data*/ object `toInt`      : PostfixUnaryOperator
}



object SetOperator {
  /*data*/ object `contains`: BinaryOperator, YieldsBool
  /*data*/ object `nonEmpty`: PostfixUnaryOperator, YieldsBool
  /*data*/ object `isEmpty` : PostfixUnaryOperator, YieldsBool
}

sealed interface AggregationOperator: Operator {
  /*data*/ object `min` : AggregationOperator
  /*data*/ object `max` : AggregationOperator
  /*data*/ object `avg` : AggregationOperator
  /*data*/ object `sum` : AggregationOperator
  /*data*/ object `size`: AggregationOperator
}
