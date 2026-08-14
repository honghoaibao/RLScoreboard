package dev.rlscoreboard.storage

/**
 * Persistence abstraction for future features that need it (historical leaderboard
 * snapshots, an offline-player stats cache, etc). V1 ships only [InMemoryStorage] since
 * neither the scoreboard engine nor the built-in datasources need a database yet - see
 * README.md for the SQLite/MySQL plan (section 18 of the design spec).
 */
interface StorageProvider {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun delete(key: String)
}
