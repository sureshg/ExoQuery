package io.exoquery.util

// Generally these operations are meant to be used with ordered maps. We do not require
// explicitly stating that they are LinkedHashMap since Iterable<Pair<K, V>>.toMap()
// in Kotlin automatically uses a LinkedHashMap if there is more than one element.
// If that changes in the future this decision can be reconsidered.

fun <K, V, R> Map<K, V>.zipWith(m2: Map<K, V>, f: (K, V, V?) -> R?): List<R> =
  this.toList().map { (k, v) -> f(k, v, m2[k]) }.filterNotNull()

fun <K, V, R> Map<K, V>.outerZipWith(m2: Map<K, V>, f: (K, V?, V?) -> R?): LinkedHashSet<R> =
  LinkedHashSet((this.keys + m2.keys).map { k -> f(k, this[k], m2[k]) }.filterNotNull())



//object LinkedHashMapOps {
//  implicit final class LinkedHashMapExt[K, V](private val m1: mutable.LinkedHashMap[K, V]) extends AnyVal {
//    def zipWith[R](m2: mutable.LinkedHashMap[K, V])(f: PartialFunction[(K, V, Option[V]), R]): List[R] =
//      LinkedHashMapOps.zipWith(m1, m2, f)
//
//    def outerZipWith[R](m2: mutable.LinkedHashMap[K, V])(
//      f: PartialFunction[(K, Option[V], Option[V]), R]
//    ): mutable.LinkedHashSet[R] =
//      LinkedHashMapOps.outerZipWith(m1, m2, f)
//  }
//
//  def zipWith[K, V, R](
//    m1: mutable.LinkedHashMap[K, V],
//    m2: mutable.LinkedHashMap[K, V],
//    f: PartialFunction[(K, V, Option[V]), R]
//  ): List[R] =
//    m1.toList.map(r => (r._1, r._2, m2.get(r._1))).collect(f)
//
//  def outerZipWith[K, V, R](
//    m1: mutable.LinkedHashMap[K, V],
//    m2: mutable.LinkedHashMap[K, V],
//    f: PartialFunction[(K, Option[V], Option[V]), R]
//  ): mutable.LinkedHashSet[R] =
//    mutable.LinkedHashSet((m1.keySet.toList ++ m2.keySet.toList): _*).map(k => (k, m1.get(k), m2.get(k))).collect(f)
//}