package dev.rlscoreboard.integration.placeholderapi

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.integration.Integration
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit

/**
 * Bridges PlaceholderAPI both ways: registers RLScoreboard's own expansion (so other
 * plugins can read `%rlscoreboard_*%`), and hooks [dev.rlscoreboard.placeholder.PlaceholderEngine]
 * so any `%xxx%` RLScoreboard doesn't recognize itself falls through to PAPI - this is how
 * `%vault_eco_balance_formatted%`, `%jobs_job%`, etc. from other plugins work in board/leaderboard
 * lines without RLScoreboard depending on those plugins directly (section 7).
 */
class PlaceholderAPIIntegration(private val plugin: RLScoreboardPlugin) : Integration {
    override val id = "PlaceholderAPI"

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null

    override fun enable() {
        RLPlaceholderExpansion(plugin).register()
        plugin.placeholderEngine.placeholderApiBridge = { player, text -> PlaceholderAPI.setPlaceholders(player, text) }
    }
}
