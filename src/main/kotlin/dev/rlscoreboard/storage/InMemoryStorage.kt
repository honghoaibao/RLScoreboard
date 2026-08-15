package dev.rlscoreboard.storage

import java.util.concurrent.ConcurrentHashMap

class InMemoryStorage : StorageProvider {
    private val map = ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun set(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}
