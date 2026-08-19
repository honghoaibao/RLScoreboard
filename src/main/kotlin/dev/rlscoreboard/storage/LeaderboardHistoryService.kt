package dev.rlscoreboard.storage

import dev.rlscoreboard.leaderboard.LeaderboardManager
import dev.rlscoreboard.leaderboard.RankingEngine
import dev.rlscoreboard.storage.sql.LeaderboardHistoryRepository
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * Periodically records a timestamped snapshot of each leaderboard's current top-N into
 * [LeaderboardHistoryRepository]. Reads whatever [RankingEngine] already has cached
 * ([RankingEngine.peek], not [RankingEngine.entriesFor]) - never recomputes or queries a
 * datasource itself, so this adds no load beyond each leaderboard's own refresh cycle.
 */
class LeaderboardHistoryService(
    private val plugin: Plugin,
    private val leaderboardManager: LeaderboardManager,
    private val rankingEngine: RankingEngine,
    private val repository: LeaderboardHistoryRepository
) {
    private var task: BukkitTask? = null

    fun start(intervalTicks: Long) {
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { snapshotAll() }, intervalTicks, intervalTicks)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun snapshotAll() {
        val now = System.currentTimeMillis()
        val definitionIds = leaderboardManager.all().map { it.id }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            for (id in definitionIds) {
                val entries = rankingEngine.peek(id) ?: continue
                repository.saveSnapshot(id, now, entries)
            }
        })
    }
}
