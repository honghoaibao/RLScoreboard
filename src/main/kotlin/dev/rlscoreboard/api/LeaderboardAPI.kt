package dev.rlscoreboard.api

import dev.rlscoreboard.api.model.LeaderboardDefinition

interface LeaderboardAPI {
    fun getLeaderboard(id: String): LeaderboardDefinition?
    fun getLeaderboards(): Collection<LeaderboardDefinition>
    fun create(id: String, displayType: String, dataSourceId: String): LeaderboardDefinition
    fun delete(id: String): Boolean
    fun reload()

    /** Bypasses the leaderboard's own cache/interval and refreshes it on the next heartbeat. */
    fun forceRefresh(id: String)
}
