package dev.rlscoreboard.integration.auraskills

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.integration.Integration
import dev.rlscoreboard.leaderboard.datasource.AuraSkillsPowerLevelDataSource
import org.bukkit.Bukkit

/** Registers the native "auraskills_powerlevel" leaderboard datasource ([AuraSkillsPowerLevelDataSource]). */
class AuraSkillsIntegration(private val plugin: RLScoreboardPlugin) : Integration {
    override val id = "AuraSkills"

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("AuraSkills") != null

    override fun enable() {
        plugin.leaderboardEngine.dataSources.register(AuraSkillsPowerLevelDataSource())
    }
}
