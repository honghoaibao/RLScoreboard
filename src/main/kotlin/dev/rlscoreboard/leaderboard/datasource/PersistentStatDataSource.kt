package dev.rlscoreboard.leaderboard.datasource

import dev.rlscoreboard.storage.sql.PlayerStatsRepository
import org.bukkit.plugin.Plugin

/**
 * Offline-inclusive counterpart to [StatisticDataSource]/[EconomyDataSource] - ranks every
 * player who's ever had [statKey] recorded, not just who's online right now. One class
 * handles every "*_alltime" datasource (topkills_alltime, topdeaths_alltime,
 * topplaytime_alltime, economy_alltime); only [statKey] differs between them.
 */
class PersistentStatDataSource(
    plugin: Plugin,
    id: String,
    private val statKey: String,
    private val repository: PlayerStatsRepository,
    refreshIntervalTicks: Long
) : AsyncRefreshingDataSource(plugin, id, refreshIntervalTicks) {

    // A generous fixed cap - RankingEngine/SidebarLeaderboardRenderer trim down to each
    // leaderboard's own configured `entries:` count after this.
    private companion object {
        const val FETCH_LIMIT = 100
    }

    override fun fetch() = repository.topN(statKey, FETCH_LIMIT)
}
