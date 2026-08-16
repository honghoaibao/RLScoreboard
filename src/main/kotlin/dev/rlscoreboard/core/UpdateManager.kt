package dev.rlscoreboard.core

import dev.rlscoreboard.leaderboard.LeaderboardEngine
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * The single scheduled task in the whole plugin. Everything else (board refresh cadence,
 * leaderboard refresh cadence, animation frame timing) is derived from elapsed time inside
 * this one heartbeat rather than each feature scheduling its own task - see sections 9, 17
 * and 19 of the design spec ("không tạo task riêng cho mỗi player/leaderboard").
 */
class UpdateManager(
    private val plugin: Plugin,
    private val scoreboardEngine: ScoreboardEngine,
    private val leaderboardEngine: LeaderboardEngine,
    private val heartbeatTicks: Long
) {
    private var task: BukkitTask? = null

    fun start() {
        stop()
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val now = System.currentTimeMillis()
            for (player in Bukkit.getOnlinePlayers()) {
                scoreboardEngine.tick(player, now)
            }
            leaderboardEngine.tick(now)
        }, heartbeatTicks, heartbeatTicks)
    }

    fun stop() {
        task?.cancel()
        task = null
    }
}
