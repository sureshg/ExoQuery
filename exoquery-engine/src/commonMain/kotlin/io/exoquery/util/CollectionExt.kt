package io.exoquery.util

fun <K, V> Iterable<Pair<K, V>>.toLinkedMap(): LinkedHashMap<K, V> =
  LinkedHashMap<K, V>().apply {
    for ((k, v) in this@toLinkedMap) {
      put(k, v)
    }
  }
