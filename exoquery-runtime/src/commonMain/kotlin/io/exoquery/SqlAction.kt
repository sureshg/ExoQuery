package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.sql.SqlIdiom
import io.exoquery.xr.RuntimeBuilder
import io.exoquery.xr.XR

data class SqlAction<Input, Output>(override val xr: XR.Action, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfActionXR {
  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlAction<Input, Output> =
    copy(xr = xr as? XR.Action ?: xrError("Failed to rebuild SqlAction with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  fun buildRuntime(dialect: SqlIdiom, label: String?, pretty: Boolean = false): SqlCompiledAction<Input, Output> = run {
    val containerBuild = RuntimeBuilder(this, dialect, label, pretty).invoke()
    val returningType = ReturningType.fromActionXR(xr)
    SqlCompiledAction(containerBuild.queryString, containerBuild.queryTokenized, true, returningType, containerBuild.label, Phase.Runtime, { xr })
  }

  fun <Dialect: SqlIdiom> build(): SqlCompiledAction<Input, Output> = errorCap("The build function body was not inlined")
  fun <Dialect: SqlIdiom> build(label: String): SqlCompiledAction<Input, Output> = errorCap("The build function body was not inlined")

  fun <Dialect: SqlIdiom> buildPretty(): SqlCompiledAction<Input, Output> = errorCap("The buildPretty function body was not inlined")
  fun <Dialect: SqlIdiom> buildPretty(label: String): SqlCompiledAction<Input, Output> = errorCap("The buildPretty function body was not inlined")

  override fun withNonStrictEquality(): SqlAction<Input, Output> = copy(params = params.withNonStrictEquality())

  fun determinizeDynamics(): SqlAction<Input, Output> = DeterminizeDynamics().ofAction(this)
}

data class SqlActionBatch<Input, Output>(override val xr: XR.Action, val batchAlias: XR.Ident, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfActionXR {
  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlActionBatch<Input, Output> =
    copy(xr = xr as? XR.Action ?: xrError("Failed to rebuild SqlActionBatch with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  override fun withNonStrictEquality(): SqlActionBatch<Input, Output> = copy(params = params.withNonStrictEquality())
}
