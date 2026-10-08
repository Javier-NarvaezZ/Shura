package io.github.javiernarvaezz.shura.core.ui.artwork

/** A small least-recently-used cache. Not thread-safe: use it from one thread (the main thread). */
class LruCache<K, V>(
    private val capacity: Int,
) {
    private val entries = LinkedHashMap<K, V>()

    operator fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    operator fun set(
        key: K,
        value: V,
    ) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}
