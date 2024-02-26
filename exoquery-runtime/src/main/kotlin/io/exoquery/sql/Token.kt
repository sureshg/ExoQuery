package io.exoquery.sql

import io.exoquery.util.intersperseWith
import io.exoquery.util.mkString

sealed interface Token
sealed interface TagToken: Token

final data class StringToken(val string: String): Token {
  override fun toString(): String = string
}

//final data class ScalarTagToken(tag: ScalarTag): TagToken {
//  override fun toString: String = s"lift(${tag.uid})"
//}

final data class ValuesClauseToken(val statement: Statement): Token {
  override fun toString(): String = statement.toString()
}

final data class Statement(val tokens: List<Token>): Token {
  override fun toString(): String = tokens.mkString()
}

final data class SetContainsToken(val a: Token, val op: Token, val b: Token): Token {
  override fun toString(): String = "${a.toString()} ${op.toString()} (${b.toString()})"
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








