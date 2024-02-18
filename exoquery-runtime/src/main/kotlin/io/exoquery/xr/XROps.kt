package io.exoquery.xr

fun XR.Map.containsImpurities(): Boolean =
  CollectXR(this) {
    with(it) {
      when {
        this is XR.Aggregation -> this
        this is XR.Infix && !this.pure -> this
        else -> null
      }
    }
  }.isNotEmpty()


  /*
  def unapply(ast: Ast) =
  CollectAst(ast) {
    case agg: Aggregation          => agg
      case inf: Infix if (!inf.pure) => inf
  }.nonEmpty
   */