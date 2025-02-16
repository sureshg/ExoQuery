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



class ContainsXR(private val predicate: (XR) -> Boolean): StatefulTransformer<Boolean> {

  var isFound = false
  override val state get() = isFound

  override fun invoke(xr: XR.Expression): Pair<XR.Expression, StatefulTransformer<Boolean>> =
    if (isFound) xr to this
    else { isFound = predicate(xr); xr to this }

  override fun invoke(xr: XR.Query): Pair<XR.Query, StatefulTransformer<Boolean>> =
    if (isFound) xr to this
    else { isFound = predicate(xr); xr to this }

  override fun invoke(xr: XR.Branch): Pair<XR.Branch, StatefulTransformer<Boolean>> =
    if (isFound) xr to this
    else { isFound = predicate(xr); xr to this }

  override fun invoke(xr: XR.Variable): Pair<XR.Variable, StatefulTransformer<Boolean>> =
    if (isFound) xr to this
    else { isFound = predicate(xr); xr to this }

  override fun invoke(xr: XR): Pair<XR, StatefulTransformer<Boolean>> =
    if (isFound) xr to this
    else { isFound = predicate(xr); xr to this }


  companion object {
    operator fun <T> invoke(xr: XR, collect: (XR) -> T?): List<T> where T: XR =
      CollectXR<T>(collect).root(xr).second.state
  }
}

open class TransformXR(
  val transformExpression: (XR.Expression) -> XR.Expression? = { it },
  val transformQuery: (XR.Query) -> XR.Query? = { it },
  val transformBranch: (XR.Branch) -> XR.Branch? = { it },
  val transformVariable: (XR.Variable) -> XR.Variable? = { it },
): StatelessTransformer {

  // For each transform, if the transform-function actually "captures" the given XR use the result of that,
  // otherwise go to the super-class to recurse down into respective nodes
  override fun invoke(xr: XR.Expression): XR.Expression = transformExpression(xr) ?: super.invoke(xr)
  override fun invoke(xr: XR.Query): XR.Query = transformQuery(xr) ?: super.invoke(xr)
  override fun invoke(xr: XR.Branch): XR.Branch = transformBranch(xr) ?: super.invoke(xr)

  companion object {
    fun <T: XR> LikeToLike(xr: T, transform: (T) -> T?): T =
      TransformXR(
        transformExpression = { if (xr is XR.Expression) transform(xr) as XR.Expression else it as XR.Expression },
        transformQuery = { if (xr is XR.Query) transform(xr) as XR.Query else it as XR.Query },
        transformBranch = { if (xr is XR.Branch) transform(xr) as XR.Branch else it as XR.Branch },
        transformVariable = { if (xr is XR.Variable) transform(xr) as XR.Variable else it as XR.Variable }
      ).invoke(xr) as T

    fun Query(xr: XR.Query, transform: (XR.Query) -> XR.Query?): XR.Query =
      TransformXR(transformQuery = transform).invoke(xr) as XR.Query

    fun Expression(xr: XR.Expression, transform: (XR.Expression) -> XR.Expression?): XR.Expression =
      TransformXR(transformExpression = transform).invoke(xr) as XR.Expression

    fun Branch(xr: XR.Branch, transform: (XR.Branch) -> XR.Branch?): XR.Branch =
      TransformXR(transformBranch = transform).invoke(xr) as XR.Branch

    fun Variable(xr: XR.Variable, transform: (XR.Variable) -> XR.Variable?): XR.Variable =
      TransformXR(transformVariable = transform).invoke(xr) as XR.Variable

    fun build() = TransformerBuilder()
  }
}

class TransformerBuilder {
  @PublishedApi internal var transformExpression: ((XR.Expression) -> XR.Expression?)? = null
  @PublishedApi internal var transformQuery: ((XR.Query) -> XR.Query?)? = null

  fun withQuery(transform: (XR.Query) -> XR.Query?) = run {
    transformQuery = transform
    this
  }
  fun withExpression(transform: (XR.Expression) -> XR.Expression?) = run {
    transformExpression = transform
    this
  }

  internal inline fun <reified XRQ: XR.Query> withQueryOf(crossinline transform: (XRQ) -> XR.Query?) = run {
    transformQuery = { if (it is XRQ) transform(it) else null /* note if this returns a non-null value the transform recursion will stop so need to return null until an actual match is made */ }
    this
  }
  internal inline fun <reified XRE: XR.Expression> withExpressionOf(crossinline transform: (XRE) -> XR.Expression?) = run {
    transformExpression = { if (it is XRE) transform(it) else null /* note if this returns a non-null value the transform recursion will stop so need to return null until an actual match is made */ }
    this
  }

  operator fun invoke(xr: XR) = TransformXR(transformExpression ?: { it }, transformQuery ?: { it }).invoke(xr)
  operator fun invoke(xr: XR.U.QueryOrExpression): XR.U.QueryOrExpression = TransformXR(transformExpression ?: { it }, transformQuery ?: { it }).invoke(xr)
}
