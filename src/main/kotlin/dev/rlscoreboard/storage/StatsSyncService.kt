package dev.rlscoreboard.storage

import dev.rlscoreboard.storage.sql.PlayerStatsRepository
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

private data class PlayerSnapshot(val uuid: UUID, val name: String, val statValues: Map<String, Double>, val economyValue: Double?)

/**
 * Periodically snapshots online players' live stats into [PlayerStatsRepository] so the
 * "*_alltime" leaderboard datasources always have a recent value even for players who then
 * log off. Reads Bukkit/Vault state on the main thread (required - that API isn't
 * thread-safe) and only ever pushes the actual DB writes to an async task.
 */
class StatsSyncService(
    private val plugin: Plugin,
    private val repository: PlayerStatsRepository,
    private val statistics: Map<String, Statistic>,
    private val economyProvider: () -> Economy?
) {
    private var task: BukkitTask? = null

    fun start(intervalTicks: Long) {
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { syncOnlinePlayers() }, intervalTicks, intervalTicks)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /** Main-thread only. */
    fun syncOnlinePlayers() {
        val snapshots = Bukkit.getOnlinePlayers().map { toSnapshot(it) }
        if (snapshots.isEmpty()) return
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { persist(snapshots) })
    }

    /** Main-thread only - call from PlayerQuitEvent so a player's last values are saved before they go offline. */
    fun syncOnQuit(player: Player) {
        val snapshot = toSnapshot(player)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { persist(listOf(snapshot)) })
    }

    private fun toSnapshot(player: Player): PlayerSnapshot {
        val values = statistics.mapValues { (_, stat) -> runCatching { player.getStatistic(stat) }.getOrDefault(0).toDouble() }
        val balance = economyProvider()?.let { eco -> runCatching { eco.getBalance(player) }.getOrNull() }
        return PlayerSnapshot(player.uniqueId, player.name, values, balance)
    }

    private fun persist(snapshots: List<PlayerSnapshot>) {
        for (snap in snapshots) {
            for ((key, value) in snap.statValues) repository.upsert(snap.uuid, snap.name, key, value)
            snap.economyValue?.let { repository.upsert(snap.uuid, snap.name, "economy", it) }
        }
    }
}
