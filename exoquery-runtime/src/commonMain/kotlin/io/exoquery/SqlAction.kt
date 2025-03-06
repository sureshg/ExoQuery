package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.xr.XR

data class SqlAction<Input, Output>(override val xr: XR.Action, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfActionXR {
  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlAction<Input, Output> =
    copy(xr = xr as? XR.Action ?: xrError("Failed to rebuild SqlAction with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  override fun withNonStrictEquality(): SqlAction<Input, Output> = copy(params = params.withNonStrictEquality())
}

data class SqlActionBatch<Input, Output>(override val xr: XR.Action, val batchAlias: XR.Ident, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfActionXR {
  fun show() = PrintMisc().invoke(this)
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlActionBatch<Input, Output> =
    copy(xr = xr as? XR.Action ?: xrError("Failed to rebuild SqlActionBatch with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  override fun withNonStrictEquality(): SqlActionBatch<Input, Output> = copy(params = params.withNonStrictEquality())
}
