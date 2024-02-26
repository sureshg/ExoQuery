package io.exoquery.util

val <T> List<T>.head get() = this.first()
val <T> List<T>.tail get() =
  if (this.isEmpty()) throw IllegalStateException("Cannot get the tail of an empty List")
  else this.subList(1, this.size)

fun <T> MutableList<T>.withMore(token: T): MutableList<T> {
  add(token)
  return this
}

fun <T> List<T>.intersperseWith(other: List<T>): List<T> {
  tailrec fun rec(accum: List<T>, a: List<T>, b: List<T>): List<T> =
    when {
      a.isNotEmpty() -> rec(accum + a.head, b, a.tail)
      else           -> accum + b
    }
  return rec(listOf(), this, other)
}
