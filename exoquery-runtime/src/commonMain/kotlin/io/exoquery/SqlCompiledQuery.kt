package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.sql.ParamBatchToken
import io.exoquery.sql.ParamBatchTokenRealized
import io.exoquery.sql.ParamMultiToken
import io.exoquery.sql.ParamMultiTokenRealized
import io.exoquery.sql.ParamSingleToken
import io.exoquery.sql.ParamSingleTokenRealized
import io.exoquery.sql.SetContainsToken
import io.exoquery.sql.SqlQueryModel
import io.exoquery.sql.StatelessTokenTransformer
import io.exoquery.sql.Statement
import io.exoquery.sql.StringToken
import io.exoquery.sql.Token
import io.exoquery.sql.TokenContext
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




// Effective token refines tokens for that particular batch-input value, we only need it for debugging
data class BatchParamGroup<BatchInput, Input: Any, Output>(val input: BatchInput, val params: List<Param<*>>, val effectiveToken: () -> Token) {
  fun determinizeDynamics(): BatchParamGroup<BatchInput, Input, Output> = run {
    val (token, params) = determinizeToken(effectiveToken(), params)
    this.copy(params = params, effectiveToken = { token })
  }
}

// TODO since we're providing the batch-parameter at the last moment, we need a function that replaces ParamBatchRefiner to ParamSingle instances and create BatchGroups
data class SqlCompiledBatchAction<BatchInput, Input: Any, Output>(val value: String, override val token: Token, val needsTokenization: Boolean, val batchParam: Sequence<BatchInput>, val label: String?, val debugData: SqlCompiledBatchAction.DebugData): ExoCompiled() {
  override val params: List<Param<*>> by lazy { token.extractParams() }

  val effectiveQuery by lazy { token.build() }

  protected fun paramsForElement(elem: BatchInput) =
    params.map { param ->
      when (param) {
        is ParamBatchRefiner<*, *> -> param.refineAny(elem as Any?)
        else -> param
      }
    }


  private fun realizeToken(token: Token, value: BatchInput) =
    StatelessTokenTransformer.invoke {
      when (it) {
        is ParamBatchTokenRealized -> {
          it.refineAny(value)
        }
        else -> null
      }
    }.invoke(token)

  fun produceBatchGroups(): Sequence<BatchParamGroup<BatchInput, Input, Output>> =
    batchParam.map { BatchParamGroup(it, paramsForElement(it), { realizeToken(token, it) }) }


  override fun determinizeDynamics(): SqlCompiledBatchAction<BatchInput, Input, Output> =
    this.copy(token = determinizedToken())

  fun withNonStrictEquality() = this.copy(token = token.withNonStrictEquality())

  fun show() = PrintMisc().invoke(this)

  data class DebugData(val phase: Phase, val originalXR: () -> XR.Batching)
}

abstract class ExoCompiled {
  abstract val params: List<Param<*>>
  abstract val token: Token

  abstract fun determinizeDynamics(): ExoCompiled

  protected fun determinizedToken() = determinizeToken(token, params).first
}

fun determinizeToken(token: Token, params: List<Param<*>>): Pair<Token, List<Param<*>>> {
  var id = 0
  fun nextId() = "$id".also { id++ }
  val bids = params.map { param ->
    val newId = BID(nextId())
    (param.id to newId) to param.withNewBid(newId)
  }
  val (bidMap, newParams) = bids.unzip()
  return token.mapBids(bidMap.toMap()) to newParams
}
