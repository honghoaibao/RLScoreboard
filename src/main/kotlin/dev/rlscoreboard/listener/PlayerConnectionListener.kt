package dev.rlscoreboard.listener

import dev.rlscoreboard.RLScoreboardPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerConnectionListener(private val plugin: RLScoreboardPlugin) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.scoreboardEngine.tick(event.player, System.currentTimeMillis())
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.scoreboardEngine.clearFor(event.player)
        // Save this player's final stat values before they go offline, so "*_alltime"
        // leaderboards reflect their latest kills/deaths/playtime/balance immediately
        // rather than waiting for the next periodic sync.
        plugin.statsSyncService?.syncOnQuit(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        // Re-evaluate immediately on world change instead of waiting for the next heartbeat,
        // so world-gated boards (see scoreboards/lobby.yml) switch without visible delay.
        plugin.scoreboardEngine.tick(event.player, System.currentTimeMillis())
    }
}
