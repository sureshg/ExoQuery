package io.exoquery

typealias Quad<A, B, C, D> = Tuple4<A, B, C, D>
typealias Quin<A, B, C, D, E> = Tuple5<A, B, C, D, E>

data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
data class Tuple6<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)
data class Tuple7<A, B, C, D, E, F, G>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F, val seventh: G)

fun <A, B> tupleOf(a: A, b: B): Pair<A, B> = Pair(a, b)
fun <A, B, C> tupleOf(a: A, b: B, c: C): Triple<A, B, C> = Triple(a, b, c)
fun <A, B, C, D> tupleOf(a: A, b: B, c: C, d: D): Tuple4<A, B, C, D> = Tuple4(a, b, c, d)
fun <A, B, C, D, E> tupleOf(a: A, b: B, c: C, d: D, e: E): Tuple5<A, B, C, D, E> = Tuple5(a, b, c, d, e)
fun <A, B, C, D, E, F> tupleOf(a: A, b: B, c: C, d: D, e: E, f: F): Tuple6<A, B, C, D, E, F> = Tuple6(a, b, c, d, e, f)
fun <A, B, C, D, E, F, G> tupleOf(a: A, b: B, c: C, d: D, e: E, f: F, g: G): Tuple7<A, B, C, D, E, F, G> = Tuple7(a, b, c, d, e, f, g)
