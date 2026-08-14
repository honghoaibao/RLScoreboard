package dev.rlscoreboard.leaderboard.datasource

import com.gamingmesh.jobs.Jobs
import dev.rlscoreboard.api.LeaderboardDataSource
import dev.rlscoreboard.api.model.LeaderboardEntry
import org.bukkit.Bukkit

/**
 * Ranks currently-online players by their total Jobs Reborn level - the sum of their level
 * in every job they've joined - using the real Jobs API rather than PlaceholderAPI
 * passthrough, so it can actually be sorted numerically.
 *
 * RISK NOTE: see the comment on [dev.rlscoreboard.integration.jobs.JobsIntegration] - this
 * was written without a local build to compile-check against. Every call is wrapped in
 * `runCatching` so a runtime API mismatch degrades to "no entries" rather than an error
 * spamming the console, but a *compile-time* mismatch (a renamed method) would fail the
 * whole build - this is the one file to fix or delete if that happens.
 */
class JobsTotalLevelDataSource : LeaderboardDataSource {
    override val id = "jobs_totallevel"

    override fun isAvailable(): Boolean = true

    override fun getEntries(): List<LeaderboardEntry> =
        Bukkit.getOnlinePlayers().mapNotNull { player ->
            val jobsPlayer = runCatching { Jobs.getPlayerManager().getJobsPlayer(player) }.getOrNull()
                ?: return@mapNotNull null
            val totalLevel = runCatching {
                jobsPlayer.getJobProgression().sumOf { progression -> progression.getLevel() }
            }.getOrNull() ?: return@mapNotNull null
            LeaderboardEntry(player.uniqueId, player.name, totalLevel.toDouble())
        }
}
