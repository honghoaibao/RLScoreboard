package dev.rlscoreboard.leaderboard.datasource

import dev.rlscoreboard.api.LeaderboardDataSource
import dev.rlscoreboard.api.model.LeaderboardEntry
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * Base for data sources backed by something too slow/blocking to call from
 * [LeaderboardDataSource.getEntries] directly - a database, a remote API, anything that
 * isn't cheap. Runs [fetch] on a repeating **async** task and serves the last successful
 * result instantly and synchronously from [getEntries], which is what keeps
 * [dev.rlscoreboard.leaderboard.RankingEngine] (called from the main-thread heartbeat)
 * safe even when the real work is a database query - section 17 of the design spec:
 * "main thread chỉ làm Bukkit API calls an toàn". Not [LeaderboardDataSource.isAvailable]
 * until the first successful fetch completes, so a fresh server doesn't briefly show an
 * empty leaderboard as "available".
 */
abstract class AsyncRefreshingDataSource(
    private val plugin: Plugin,
    override val id: String,
    private val refreshIntervalTicks: Long
) : LeaderboardDataSource {

    @Volatile private var snapshot: List<LeaderboardEntry> = emptyList()
    @Volatile private var ready = false
    private var task: BukkitTask? = null

    /** Runs off the main thread - safe to block here. */
    protected abstract fun fetch(): List<LeaderboardEntry>

    open fun start() {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            val result = runCatching { fetch() }.getOrNull()
            if (result != null) {
                snapshot = result
                ready = true
            }
        }, 0L, refreshIntervalTicks)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    override fun isAvailable(): Boolean = ready
    override fun getEntries(): List<LeaderboardEntry> = snapshot
}
