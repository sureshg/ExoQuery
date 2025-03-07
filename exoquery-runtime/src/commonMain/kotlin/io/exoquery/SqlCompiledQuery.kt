package io.exoquery

import io.exoquery.sql.Token

sealed interface Phase {
  object CompileTime : Phase
  object Runtime : Phase
}

// TODO value needs to be Token and we need to write a lifter for Token
//      This probably needs to be something like SqlCompiledQuery<T> which has constructors
//      SqlCompiledQuery.compileTime<T>(String|Token,Params,serialier<T>=some-default-value) and SqlCompiledQuery.runtime(query:SqlQuery,serialier<T>=some-default-value)
//      (actually come to think of it, we can probably implement the dynamic path directly and have the staic path replace the build() method if it's possible)
// needsTokenization is a flag indicating whether we need to call token.build to get the query or if we
// can just use the value-string. Typically we cannot use the value-string because there is a paramList (since we don't know how many instances of "?" to use)
// This needs to be checked from the Token at compile-time (also if the dialect requires numbered parameters
// it is also useful to use Token)
data class SqlCompiledQuery<T>(val value: String, override val token: Token, val needsTokenization: Boolean, val label: String?, val phase: Phase): ExoCompiled() {
  override val params: List<Param<*>> by lazy { token.extractParams() }

  // Similar concept tot the SqlQuery/SqlExpression.determinizeDynamics but it does not need to consider any nesting constructs
  // because the Params in the `params` variable are already determined to be the complete set in the tokenization
  // (determined by Lifter.liftToken + realization for compile-time and buildRuntime + realization for runtime)
  fun determinizeDynamics(): SqlCompiledQuery<T> =
    this.copy(token = determinizedToken())
}

data class SqlCompiledAction<Input, Output>(val value: String, override val token: Token, val needsTokenization: Boolean, val label: String?, val phase: Phase): ExoCompiled() {
  override val params: List<Param<*>> by lazy { token.extractParams() }

  fun determinizeDynamics(): SqlCompiledAction<Input, Output> =
    this.copy(token = determinizedToken())
}

abstract class ExoCompiled {
  abstract val params: List<Param<*>>
  abstract val token: Token

  protected fun determinizedToken() = run {
    var id = 0
    fun nextId() = "$id".also { id++ }
    val bids = params.map { param ->
      val newId = BID(nextId())
      (param.id to newId) to param.withNewBid(newId)
    }
    val (bidMap, newParams) = bids.unzip()
    token.mapBids(bidMap.toMap())
  }
}
