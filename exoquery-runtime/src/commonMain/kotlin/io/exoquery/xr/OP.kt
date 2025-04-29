package io.exoquery.xr

import kotlinx.serialization.Serializable


@Serializable
sealed interface UnaryOperator : OP
@Serializable
sealed interface PrefixUnaryOperator : UnaryOperator
@Serializable
sealed interface PostfixUnaryOperator : UnaryOperator
@Serializable
sealed interface BinaryOperator : OP
@Serializable
sealed interface YieldsBool

@Serializable
sealed interface SetOperator : OP
@Serializable
sealed interface AggregationOperator : OP
@Serializable
sealed interface StringOperator : OP
@Serializable
sealed interface NumericOperator : OP
@Serializable
sealed interface BooleanOperator : OP
@Serializable
sealed interface EqualityOperator : OP

/**
 * Just operators used for symbolic expression in SQL dialects. The Quill Operators object had operators
 * like Query.isEmpty, Query.contains, etc... This is handled by XR.MethodCall and XR.GlobalCall in ExoQuery
 */
@Serializable
sealed interface OP {
  val symbol: String

  @Serializable
  data object `==` : BinaryOperator, EqualityOperator, YieldsBool {
    override val symbol = "==";
    override fun toString() = symbol
  }

  @Serializable
  data object `!=` : BinaryOperator, EqualityOperator, YieldsBool {
    override val symbol = "!=";
    override fun toString() = symbol
  }

  @Serializable
  data object not : BooleanOperator, PrefixUnaryOperator, YieldsBool {
    override val symbol = "!"
  }

  @Serializable
  data object and : BooleanOperator, BinaryOperator, YieldsBool {
    override val symbol = "&&"
  }

  @Serializable
  data object or : BooleanOperator, BinaryOperator, YieldsBool {
    override val symbol = "||"
  }


  @Serializable
  data object plus : NumericOperator, BinaryOperator {
    override val symbol = "+"
  }

  @Serializable
  data object minus : NumericOperator, BinaryOperator, PrefixUnaryOperator {
    override val symbol = "-"
  }

  @Serializable
  data object mult : NumericOperator, BinaryOperator {
    override val symbol = "*"
  }

  @Serializable
  data object gt : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = ">"
  }

  @Serializable
  data object gte : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = ">="
  }

  @Serializable
  data object lt : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = "<"
  }

  @Serializable
  data object lte : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = "<="
  }

  @Serializable
  data object div : NumericOperator, BinaryOperator {
    override val symbol = "/"
  }

  @Serializable
  data object mod : NumericOperator, BinaryOperator {
    override val symbol = "%"
  }

  @Serializable
  data object strPlus : StringOperator, BinaryOperator {
    override val symbol = "+"
  }
}
