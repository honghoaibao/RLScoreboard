package dev.rlscoreboard.leaderboard.datasource

import dev.aurelium.auraskills.api.AuraSkillsApi
import dev.rlscoreboard.api.LeaderboardDataSource
import dev.rlscoreboard.api.model.LeaderboardEntry
import org.bukkit.Bukkit

/**
 * Ranks currently-online players by their AuraSkills "power level" - a built-in AuraSkills
 * concept that's already the sum of all their individual skill levels, so no aggregation
 * logic is needed on RLScoreboard's side. Backed by the official `auraskills-api-bukkit`
 * artifact and wiki-documented methods - higher confidence than the Jobs datasource above.
 */
class AuraSkillsPowerLevelDataSource : LeaderboardDataSource {
    override val id = "auraskills_powerlevel"

    override fun isAvailable(): Boolean = true

    override fun getEntries(): List<LeaderboardEntry> {
        val api = runCatching { AuraSkillsApi.get() }.getOrNull() ?: return emptyList()
        return Bukkit.getOnlinePlayers().mapNotNull { player ->
            val level = runCatching { api.getUser(player.uniqueId).getPowerLevel() }.getOrNull()
                ?: return@mapNotNull null
            LeaderboardEntry(player.uniqueId, player.name, level.toDouble())
        }
    }
}
