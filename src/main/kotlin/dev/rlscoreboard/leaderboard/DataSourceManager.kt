package dev.rlscoreboard.leaderboard

import dev.rlscoreboard.api.LeaderboardDataSource
import java.util.concurrent.ConcurrentHashMap

/** Registry of [LeaderboardDataSource]s - RLScoreboard's built-ins plus anything third-party plugins register via the API. */
class DataSourceManager {
    private val sources = ConcurrentHashMap<String, LeaderboardDataSource>()

    fun register(source: LeaderboardDataSource) {
        sources[source.id.lowercase()] = source
    }

    fun unregister(id: String) {
        sources.remove(id.lowercase())
    }

    fun get(id: String): LeaderboardDataSource? = sources[id.lowercase()]

    fun all(): Collection<LeaderboardDataSource> = sources.values
}
