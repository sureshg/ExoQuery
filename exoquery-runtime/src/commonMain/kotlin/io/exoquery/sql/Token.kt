package io.exoquery.sql

import io.exoquery.BID
import io.exoquery.Param
import io.exoquery.ParamMulti
import io.exoquery.ParamSet
import io.exoquery.ParamSingle
import io.exoquery.util.intersperseWith
import io.exoquery.util.mkString
import io.exoquery.xrError

data class Shower(val showBids: Boolean = true, val showValues: Boolean = true, val truncateBids: Int? = 6) {
  private fun BID.trunc() =
    if (truncateBids != null)
      this.value.toString().takeLast(truncateBids)
    else
      this.value.toString()

  private fun Param<*>?.showParam() = this?.let { param -> param.showValue() } ?: "<NOT_FOUND>"

  fun show(bid: BID, value: Param<*>?): String {
    val string = when {
      showBids && showValues -> "{${bid.trunc()}:${value.showParam()}}"
      showBids -> "{${bid.trunc()}${if (value != null) "" else ":!!"}}"
      showValues -> value.showParam()
      else -> "?"
    }
    return string
}
}

sealed interface Token {
  // Builds the actual string to be used as the SQL query as opposed to just for display purposes
  fun build(): String
  // For cases where it is a Param actually plugin the value i.e. stringify it
  fun show(shower: Shower): String
}
sealed interface TagToken: Token

final data class StringToken(val string: String): Token {
  override fun build(): String = string
  override fun show(shower: Shower): String = string
}

final data class ParamSingleToken(val bid: BID): Token {
  override fun build() = "?"
  fun realize(paramSet: ParamSet): ParamSingleTokenRealized =
    ParamSingleTokenRealized(bid, paramSet.lifts.asSequence().filterIsInstance<ParamSingle<*>>().find { p -> p.id == bid })
  override fun show(shower: Shower): String = shower.show(bid, null)
}

// Allow for the possibility of `param` being an error so that we can introspect the tree for errors
// withiout immediately failing on creation
final data class ParamSingleTokenRealized(val bid: BID, val param: ParamSingle<*>?): Token {
  override fun build(): String = param?.let { "?" } ?: xrError("Param not found for bid: ${bid}")
  override fun show(shower: Shower): String = shower.show(bid, param)
}

final data class ParamMultiToken(val bid: BID): Token {
  override fun build() = "?"
  fun realize(paramSet: ParamSet): ParamMultiTokenRealized =
    ParamMultiTokenRealized(bid, paramSet.lifts.asSequence().filterIsInstance<ParamMulti<*>>().find { p -> p.id == bid })
  override fun show(shower: Shower): String = shower.show(bid, null)
}

final data class ParamMultiTokenRealized(val bid: BID, val param: ParamMulti<*>?): Token {
  // NOTE probably more efficient to just count the param values and use .repeat() to get a list of "?"s
  override fun build(): String = param?.value?.map { _ -> "?" }?.joinToString(", ") ?: xrError("Param not found for bid: ${bid}")
  override fun show(shower: Shower): String = shower.show(bid, param)
}

final data class Statement(val tokens: List<Token>): Token {
  override fun build(): String = tokens.mkString()
  override fun show(shower: Shower): String = tokens.map { it.show(shower) }.mkString()
}

final data class SetContainsToken(val a: Token, val op: Token, val b: Token): Token {
  override fun build(): String = "${a.build()} ${op.build()} (${b.build()})"
  override fun show(shower: Shower): String = "${a.show(shower)} ${op.show(shower)} (${b.show(shower)})"
}

val Token.token get(): Token = this
val String.token get() = StringToken(this)

fun <T> List<T>.token(elemTokenizer: (T) -> Token): Statement = this.map(elemTokenizer).mkStmt()
fun List<Token>.mkStmt(sep: String = ", "): Statement =
  if (this.isEmpty())
    Statement(listOf())
  else {
    val sepList = List(this.size-1) { StringToken(sep) }
    Statement(this.intersperseWith(sepList))
  }
