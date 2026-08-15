package dev.rlscoreboard.leaderboard

import dev.rlscoreboard.api.LeaderboardDataSource
import dev.rlscoreboard.api.model.LeaderboardEntry
import dev.rlscoreboard.core.CacheManager

/**
 * Fetches, sorts and caches entries for a leaderboard, keyed per leaderboard id with a TTL
 * equal to that leaderboard's own configured update interval - so 100+ concurrent viewers
 * never trigger 100+ datasource calls per refresh (section 19).
 */
class RankingEngine {
    private val cache = CacheManager<String, List<LeaderboardEntry>>()

    fun entriesFor(leaderboardId: String, dataSource: LeaderboardDataSource, limit: Int, ttlMillis: Long): List<LeaderboardEntry> {
        if (!dataSource.isAvailable()) return emptyList()
        return cache.getOrCompute(leaderboardId, ttlMillis) {
            dataSource.getEntries().sorted().take(limit)
        }
    }

    /** Reads whatever is currently cached without triggering a recompute - used by [dev.rlscoreboard.storage.LeaderboardHistoryService] so history snapshots add zero extra datasource load. */
    fun peek(leaderboardId: String): List<LeaderboardEntry>? = cache.get(leaderboardId)

    fun invalidate(leaderboardId: String) = cache.invalidate(leaderboardId)
}
