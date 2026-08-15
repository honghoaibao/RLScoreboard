package dev.rlscoreboard.leaderboard.datasource

import dev.rlscoreboard.api.LeaderboardDataSource
import dev.rlscoreboard.api.model.LeaderboardEntry
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit

/**
 * Ranks currently online players by Vault economy balance. Only ever registered when Vault
 * *and* a compatible economy plugin are both present - see
 * [dev.rlscoreboard.integration.vault.VaultIntegration]. Scoped to online players for the
 * same I/O-cost reason as [StatisticDataSource]; an async, cached all-time version is a
 * good Phase 2 addition.
 */
class EconomyDataSource(private val economy: Economy) : LeaderboardDataSource {
    override val id: String = "economy"

    override fun isAvailable(): Boolean = true

    // Sorting happens centrally in RankingEngine.entriesFor() - see the comment in
    // StatisticDataSource for why data sources don't sort their own output.
    override fun getEntries(): List<LeaderboardEntry> =
        Bukkit.getOnlinePlayers().map { player ->
            val balance = runCatching { economy.getBalance(player) }.getOrDefault(0.0)
            LeaderboardEntry(player.uniqueId, player.name, balance)
        }
}
