package dev.rlscoreboard.api

import dev.rlscoreboard.api.model.LeaderboardEntry

/**
 * Supplies ranked entries for a leaderboard. Implementations should be cheap to call -
 * RLScoreboard caches results per leaderboard according to that leaderboard's own
 * `update.interval`, so a slow [getEntries] only ever costs one call per interval no
 * matter how many players are viewing it (see [dev.rlscoreboard.leaderboard.RankingEngine]).
 */
interface LeaderboardDataSource {
    val id: String

    /** Whether this data source can currently produce data (e.g. its backing plugin is installed). */
    fun isAvailable(): Boolean

    fun getEntries(): List<LeaderboardEntry>
}
