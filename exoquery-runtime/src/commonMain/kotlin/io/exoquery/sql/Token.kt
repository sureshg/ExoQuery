package io.exoquery.sql

import io.exoquery.BID
import io.exoquery.Param
import io.exoquery.ParamMulti
import io.exoquery.ParamSet
import io.exoquery.ParamSingle
import io.exoquery.util.intersperseWith
import io.exoquery.util.mkString
import io.exoquery.xrError

sealed interface Token {
  // Builds the actual string to be used as the SQL query as opposed to just for display purposes
  fun   build(): String
  // For cases where it is a Param actually plugin the value i.e. stringify it
  fun show(renderer: Renderer): String

  fun simplify(): Token = this

  fun mapBids(bids: Map<BID, BID>) =
    object: StatelessTokenTransformer {
      override fun invoke(token: ParamSingleToken): Token = token.withBid(bid = bids[token.bid] ?: token.bid)
      override fun invoke(token: ParamMultiToken): Token = token.withBid(bid = bids[token.bid] ?: token.bid)
      override fun invoke(token: ParamSingleTokenRealized): Token = token.withBid(bids[token.bid] ?: token.bid)
      override fun invoke(token: ParamMultiTokenRealized): Token = token.withBid(bid = bids[token.bid] ?: token.bid)
    }.invoke(this)

  fun extractParams(): List<Param<*>> {
    val accum = mutableListOf<Param<*>>()
    fun errorNotFound(bid: BID): Nothing = xrError("Param not found for bid: ${bid} in tokenization:\n${this.show(Renderer(true, true, null))}")
    fun errorUnrealizedFound(bid: BID): Nothing = xrError("Unrealized Param found for bid: ${bid} in tokenization:\n${this.show(Renderer(true, true, null))}")

    // Depth-first search of the tree to find all the params
    val toExplore = ArrayDeque<Token>(listOf(this))
    while (toExplore.isNotEmpty()) {
      val token = toExplore.removeFirst()
      when (token) {
        is ParamSingleTokenRealized -> accum.add(token.param ?: errorNotFound(token.bid))
        is ParamMultiTokenRealized -> accum.add(token.param ?: errorNotFound(token.bid))
        is Statement -> toExplore.addAll(0, token.tokens)
        is SetContainsToken -> toExplore.addAll(0, listOf(token.a, token.op, token.b))
        is ParamMultiToken -> errorUnrealizedFound(token.bid)
        is ParamSingleToken -> errorUnrealizedFound(token.bid)
        is StringToken -> {} // No params in a string token
      }
    }
    return accum
  }
}
sealed interface TagToken: Token

final data class StringToken(val string: String): Token {
  override fun build(): String = string
  override fun show(renderer: Renderer): String = string
}

final data class ParamSingleToken(val bid: BID): Token {
  override fun build() = "<UNR?>"
  fun realize(paramSet: ParamSet) =
    ParamSingleTokenRealized(bid, paramSet.lifts.asSequence().filterIsInstance<ParamSingle<*>>().find { p -> p.id == bid })
  override fun show(renderer: Renderer): String = renderer.invoke(bid, null, false)
  fun withBid(bid: BID) = ParamSingleToken(bid)
}

// Allow for the possibility of `param` being an error so that we can introspect the tree for errors
// withiout immediately failing on creation
final data class ParamSingleTokenRealized(val bid: BID, val param: ParamSingle<*>?): Token {
  override fun build(): String = param?.let { "?" } ?: xrError("Param not found for bid: ${bid}")
  override fun show(renderer: Renderer): String = renderer.invoke(bid, param, true)
  fun withBid(bid: BID) = ParamSingleTokenRealized(bid, param?.withNewBid(bid))
}

final data class ParamMultiToken(val bid: BID): Token {
  override fun build() = "<UNRS?>"
  fun realize(paramSet: ParamSet) =
    ParamMultiTokenRealized(bid, paramSet.lifts.asSequence().filterIsInstance<ParamMulti<*>>().find { p -> p.id == bid })
  override fun show(renderer: Renderer): String = renderer.invoke(bid, null, false)
  fun withBid(bid: BID) = ParamMultiToken(bid)
}

final data class ParamMultiTokenRealized(val bid: BID, val param: ParamMulti<*>?): Token {
  // NOTE probably more efficient to just count the param values and use .repeat() to get a list of "?"s
  override fun build(): String = param?.value?.map { _ -> "?" }?.joinToString(", ") ?: xrError("Param not found for bid: ${bid}")
  override fun show(renderer: Renderer): String = renderer.invoke(bid, param, true)
  fun withBid(bid: BID) = ParamMultiTokenRealized(bid, param?.withNewBid(bid))
}

final data class Statement(val tokens: List<Token>): Token {
  override fun build(): String = tokens.map { it.build() }.mkString()
  override fun show(renderer: Renderer): String = tokens.map { it.show(renderer) }.mkString()

  override fun simplify(): Token {
    val accum = mutableListOf<Token>()
    for (token in tokens) {
      if (accum.isNotEmpty() && token is StringToken && accum.last() is StringToken) {
        // Use StringBuilder here for efficiency but it is more algorithmically annoying
        accum[accum.size-1] = StringToken((accum.last() as StringToken).string + token.string)
      } else {
        accum.add(token)
      }
    }
    if (accum.size == 1) {
      return accum[0]
    } else {
      return Statement(accum)
    }
  }

}

final data class SetContainsToken(val a: Token, val op: Token, val b: Token): Token {
  override fun build(): String = "${a.build()} ${op.build()} parser(${b.build()})"
  override fun show(renderer: Renderer): String = "${a.show(renderer)} ${op.show(renderer)} (${b.show(renderer)})"
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
