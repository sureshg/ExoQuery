package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.sql.SqlQueryModel
import io.exoquery.sql.Token
import io.exoquery.xr.XR

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
data class SqlCompiledQuery<T>(val value: String, override val token: Token, val needsTokenization: Boolean, val label: String?, val debugData: SqlCompiledQuery.DebugData): ExoCompiled() {
  override val params: List<Param<*>> by lazy { token.extractParams() }

  // Similar concept tot the SqlQuery/SqlExpression.determinizeDynamics but it does not need to consider any nesting constructs
  // because the Params in the `params` variable are already determined to be the complete set in the tokenization
  // (determined by Lifter.liftToken + realization for compile-time and buildRuntime + realization for runtime)
  override fun determinizeDynamics(): SqlCompiledQuery<T> =
    this.copy(token = determinizedToken())

  data class DebugData(val phase: Phase, val originalXR: () -> XR.Query, val originalQuery: () -> SqlQueryModel)
}

data class SqlCompiledAction<Input, Output>(val value: String, override val token: Token, val needsTokenization: Boolean, val actionReturningKind: ActionReturningKind, val label: String?, val debugData: SqlCompiledAction.DebugData): ExoCompiled() {
  override val params: List<Param<*>> by lazy { token.extractParams() }

  override fun determinizeDynamics(): SqlCompiledAction<Input, Output> =
    this.copy(token = determinizedToken())

  fun show() = PrintMisc().invoke(this)

  data class DebugData(val phase: Phase, val originalXR: () -> XR.Action)
}

// TODO since we're providing the batch-parameter at the last moment, we need a function that replaces ParamBatchRefiner to ParamSingle instances and create BatchGroups
data class SqlCompiledBatchAction<Input: Any, Output>(val value: String, override val token: Token, val needsTokenization: Boolean, val batchParam: List<Input>, val label: String?, val debugData: SqlCompiledBatchAction.DebugData): ExoCompiled() {
  override val params: List<Param<*>> by lazy { token.extractParams() }

  override fun determinizeDynamics(): SqlCompiledBatchAction<Input, Output> =
    this.copy(token = determinizedToken())

  fun show() = PrintMisc().invoke(this)

  data class DebugData(val phase: Phase, val originalXR: () -> XR.Batching)
}

abstract class ExoCompiled {
  abstract val params: List<Param<*>>
  abstract val token: Token

  abstract fun determinizeDynamics(): ExoCompiled

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
