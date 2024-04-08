package io.exoquery

import io.exoquery.annotation.ExoTableConstructor
import io.exoquery.xr.XR

class Table<T> private constructor (override val xr: XR.Entity, override val binds: DynamicBinds): Query<T> {
  companion object {
    operator fun <T> invoke(): Table<T> = error("The TableQuery create-table expression was not inlined")
    fun <T> fromExpr(entity: EntityExpression) = Table<T>(entity.xr, DynamicBinds.empty())
  }
}

interface TableConstructor<T>

@ExoTableConstructor
operator fun <T> TableConstructor<T>.invoke(): Table<T> =
  error("The Table Query constructor was not inlined")
