package io.exoquery.xr

class CollectXR<T>(private val collect: (XR) -> T?): StatefulTransformerSingleRoot<MutableList<T>> {

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