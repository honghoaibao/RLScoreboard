package dev.rlscoreboard.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Small generic time-to-live cache - not a full caching framework on purpose.
 * RLScoreboard's caching needs are simple (avoid re-querying economy/statistics APIs
 * more often than a leaderboard's own configured update interval, see section 19).
 */
class CacheManager<K, V> {
    private data class Entry<V>(val value: V, val expiresAtMillis: Long)

    private val store = ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? {
        val entry = store[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAtMillis) {
            store.remove(key)
            return null
        }
        return entry.value
    }

    fun getOrCompute(key: K, ttlMillis: Long, compute: () -> V): V {
        get(key)?.let { return it }
        val value = compute()
        put(key, value, ttlMillis)
        return value
    }

    fun put(key: K, value: V, ttlMillis: Long) {
        store[key] = Entry(value, System.currentTimeMillis() + ttlMillis)
    }

    fun invalidate(key: K) {
        store.remove(key)
    }

    fun clear() = store.clear()
}
