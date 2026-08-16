package dev.rlscoreboard.leaderboard.datasource

import dev.rlscoreboard.api.LeaderboardDataSource
import dev.rlscoreboard.api.model.LeaderboardEntry
import org.bukkit.Bukkit
import org.bukkit.Statistic

/**
 * Ranks currently online players by a vanilla [Statistic] - kills, deaths, playtime, etc.
 * Needs no other plugin installed (section 10's "Top kills/Top deaths/Top playtime" work
 * out of the box). Only considers online players to avoid the I/O cost of loading every
 * offline player's data file on every refresh; a persistent, listener-fed stats cache for
 * true all-time offline rankings is a natural Phase 2 addition once storage is wired up.
 */
class StatisticDataSource(
    override val id: String,
    private val statistic: Statistic
) : LeaderboardDataSource {

    override fun isAvailable(): Boolean = true

    // No need to sort here - RankingEngine.entriesFor() sorts by LeaderboardEntry's natural
    // (highest-value-first) ordering right after calling this, so every data source can just
    // return entries in whatever order is cheapest to produce them in.
    override fun getEntries(): List<LeaderboardEntry> =
        Bukkit.getOnlinePlayers().map { player ->
            val value = runCatching { player.getStatistic(statistic) }.getOrDefault(0)
            LeaderboardEntry(player.uniqueId, player.name, value.toDouble())
        }
}
