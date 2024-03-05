package io.exoquery.xr

class CollectXR<T>(private val collect: (XR) -> T?): StatefulTransformerSingleRoot<MutableList<T>> {

  override val debug: DebugDump = DebugDump()

  override val state = mutableListOf<T>()

  override fun <X : XR> root(xr: X): Pair<X, StatefulTransformerSingleRoot<MutableList<T>>> {
    val found = collect(xr)
    if (found != null) {
      state.add(found)
    }
    return Pair(xr, this)
  }

  companion object {
    inline fun <reified T> byType(xr: XR): List<T> where T: XR =
      CollectXR<T> {
        when {
          // looks like we need the `as T?` here which looks like a bug
          it is T -> it as T?
          else -> null as T?
        }
      }.root(xr).second.state

    operator fun <T> invoke(xr: XR, collect: (XR) -> T?): List<T> where T: XR =
      CollectXR<T>(collect).root(xr).second.state
  }
}


class TransformXR(
  val transformQuery: (XR.Query) -> XR.Query? = { it },
  val transformExpression: (XR.Expression) -> XR.Expression? = { it },
  val transformBranch: (XR.Branch) -> XR.Branch? = { it },
  val transformVariable: (XR.Variable) -> XR.Variable? = { it }
): StatelessTransformer {

  override fun invoke(xr: XR.Expression): XR.Expression = transformExpression(xr) ?: xr
  override fun invoke(xr: XR.Query): XR.Query = transformQuery(xr) ?: xr
  override fun invoke(xr: XR.Branch): XR.Branch = transformBranch(xr) ?: xr
  override fun invoke(xr: XR.Variable): XR.Variable = transformVariable(xr) ?: xr

  companion object {
    fun Query(xr: XR.Query, transform: (XR.Query) -> XR.Query?): XR.Query =
      TransformXR(transformQuery = transform).invoke(xr) as XR.Query

    fun Expression(xr: XR.Expression, transform: (XR.Expression) -> XR.Expression?): XR.Expression =
      TransformXR(transformExpression = transform).invoke(xr) as XR.Expression

    fun Branch(xr: XR.Branch, transform: (XR.Branch) -> XR.Branch?): XR.Branch =
      TransformXR(transformBranch = transform).invoke(xr) as XR.Branch

    fun Variable(xr: XR.Variable, transform: (XR.Variable) -> XR.Variable?): XR.Variable =
      TransformXR(transformVariable = transform).invoke(xr) as XR.Variable
  }
}

