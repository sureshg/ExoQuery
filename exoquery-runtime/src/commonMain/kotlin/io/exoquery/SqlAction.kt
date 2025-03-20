package io.exoquery

import io.exoquery.annotation.ExoBuildFunctionLabel
import io.exoquery.printing.PrintMisc
import io.exoquery.sql.SqlIdiom
import io.exoquery.xr.RuntimeBuilder
import io.exoquery.xr.XR

data class SqlAction<Input, Output>(override val xr: XR.Action, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfXR {
  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlAction<Input, Output> =
    copy(xr = xr as? XR.Action ?: xrError("Failed to rebuild SqlAction with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  fun buildRuntime(dialect: SqlIdiom, label: String?, pretty: Boolean = false): SqlCompiledAction<Input, Output> = run {
    val containerBuild = RuntimeBuilder(dialect, pretty).forAction(this)
    val actionReturningKind = ActionReturningKind.fromActionXR(xr)
    SqlCompiledAction(containerBuild.queryString, containerBuild.queryTokenized, true, actionReturningKind, label,
      SqlCompiledAction.DebugData(Phase.Runtime, { xr })
    )
  }

  fun <Dialect: SqlIdiom> build(): SqlCompiledAction<Input, Output> = errorCap("The build function body was not inlined")
  fun <Dialect: SqlIdiom> build(@ExoBuildFunctionLabel label: String): SqlCompiledAction<Input, Output> = errorCap("The build function body was not inlined")

  fun <Dialect: SqlIdiom> buildPretty(): SqlCompiledAction<Input, Output> = errorCap("The buildPretty function body was not inlined")
  fun <Dialect: SqlIdiom> buildPretty(@ExoBuildFunctionLabel label: String): SqlCompiledAction<Input, Output> = errorCap("The buildPretty function body was not inlined")

  override fun withNonStrictEquality(): SqlAction<Input, Output> = copy(params = params.withNonStrictEquality())

  fun determinizeDynamics(): SqlAction<Input, Output> = DeterminizeDynamics().ofAction(this)
}

data class SqlBatchAction<BatchInput, Input: Any, Output>(override val xr: XR.Batching, val batchParam: Sequence<BatchInput>, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfXR {
  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlBatchAction<BatchInput, Input, Output> =
    copy(xr = xr as? XR.Batching ?: xrError("Failed to rebuild SqlBatchAction with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  fun buildRuntime(dialect: SqlIdiom, label: String?, pretty: Boolean = false): SqlCompiledBatchAction<BatchInput, Input, Output> = run {
    val containerBuild = RuntimeBuilder(dialect, pretty).forBatching(this)
    SqlCompiledBatchAction(containerBuild.queryString, containerBuild.queryTokenized, true, batchParam, label,
      SqlCompiledBatchAction.DebugData(Phase.Runtime, { xr })
    )
  }

  // TODO going to need to make sure this works in the TransformCompile phase because regular Queries and Actions only have 1-parameter max (i.e the label and it's always 1st position)
  fun <Dialect: SqlIdiom> build(): SqlCompiledBatchAction<BatchInput, Input, Output> = errorCap("The build function body was not inlined")
  fun <Dialect: SqlIdiom> build(@ExoBuildFunctionLabel label: String): SqlCompiledBatchAction<BatchInput, Input, Output> = errorCap("The build function body was not inlined")

  fun <Dialect: SqlIdiom> buildPretty(): SqlCompiledBatchAction<BatchInput, Input, Output> = errorCap("The buildPretty function body was not inlined")
  fun <Dialect: SqlIdiom> buildPretty(@ExoBuildFunctionLabel label: String): SqlCompiledBatchAction<BatchInput, Input, Output> = errorCap("The buildPretty function body was not inlined")

  override fun withNonStrictEquality(): SqlBatchAction<BatchInput, Input, Output> = copy(params = params.withNonStrictEquality())

  fun determinizeDynamics(): SqlBatchAction<BatchInput, Input, Output> = DeterminizeDynamics().ofBatchAction(this)
}
