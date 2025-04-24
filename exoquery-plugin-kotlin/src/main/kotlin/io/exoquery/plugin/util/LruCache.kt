package io.exoquery.plugin.util

class LruCache<K, V>(capacity: Int) {

  private val linkedHashMap = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
      return size > capacity
    }
  }

  fun getOrPut(key: K, defaultValue: () -> V): V =
    linkedHashMap.getOrPut(key, defaultValue)

  fun put(key: K, value: V) {
    linkedHashMap[key] = value
  }

  fun get(key: K): V? =
    linkedHashMap.get(key)
}
