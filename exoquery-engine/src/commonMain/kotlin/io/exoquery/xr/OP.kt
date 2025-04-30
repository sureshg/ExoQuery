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
  data object EqEq : BinaryOperator, EqualityOperator, YieldsBool {
    override val symbol = "==";
    override fun toString() = symbol
  }

  @Serializable
  data object NotEq : BinaryOperator, EqualityOperator, YieldsBool {
    override val symbol = "!=";
    override fun toString() = symbol
  }

  @Serializable
  data object Not : BooleanOperator, PrefixUnaryOperator, YieldsBool {
    override val symbol = "!"
  }

  @Serializable
  data object And : BooleanOperator, BinaryOperator, YieldsBool {
    override val symbol = "&&"
  }

  @Serializable
  data object Or : BooleanOperator, BinaryOperator, YieldsBool {
    override val symbol = "||"
  }


  @Serializable
  data object Plus : NumericOperator, BinaryOperator {
    override val symbol = "+"
  }

  @Serializable
  data object Minus : NumericOperator, BinaryOperator, PrefixUnaryOperator {
    override val symbol = "-"
  }

  @Serializable
  data object Mult : NumericOperator, BinaryOperator {
    override val symbol = "*"
  }

  @Serializable
  data object Gt : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = ">"
  }

  @Serializable
  data object GtEq : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = ">="
  }

  @Serializable
  data object Lt : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = "<"
  }

  @Serializable
  data object LtEq : NumericOperator, BinaryOperator, YieldsBool {
    override val symbol = "<="
  }

  @Serializable
  data object Div : NumericOperator, BinaryOperator {
    override val symbol = "/"
  }

  @Serializable
  data object Mod : NumericOperator, BinaryOperator {
    override val symbol = "%"
  }

  @Serializable
  data object StrPlus : StringOperator, BinaryOperator {
    override val symbol = "+"
  }
}
