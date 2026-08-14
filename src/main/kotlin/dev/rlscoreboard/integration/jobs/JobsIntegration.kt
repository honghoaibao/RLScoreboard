package dev.rlscoreboard.integration.jobs

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.integration.Integration
import dev.rlscoreboard.leaderboard.datasource.JobsTotalLevelDataSource
import org.bukkit.Bukkit

/**
 * Registers the native "jobs_totallevel" leaderboard datasource ([JobsTotalLevelDataSource])
 * on top of the `%jobs_*%` placeholders that already work via PlaceholderAPIIntegration.
 * See that class's doc comment for the API-stability risk note - Jobs Reborn doesn't
 * publish the same stability guarantee VaultAPI/LuckPerms API do.
 */
class JobsIntegration(private val plugin: RLScoreboardPlugin) : Integration {
    override val id = "Jobs"

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("Jobs") != null

    override fun enable() {
        plugin.leaderboardEngine.dataSources.register(JobsTotalLevelDataSource())
    }
}
