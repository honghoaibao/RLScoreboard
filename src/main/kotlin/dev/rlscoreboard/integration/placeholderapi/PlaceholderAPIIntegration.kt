package dev.rlscoreboard.integration.placeholderapi

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.integration.AbstractIntegration
import me.clip.placeholderapi.PlaceholderAPI

/**
 * Bridges PlaceholderAPI both ways: registers RLScoreboard's own expansion (so other
 * plugins can read `%rlscoreboard_*%`), and hooks [dev.rlscoreboard.placeholder.PlaceholderEngine]
 * so any `%xxx%` RLScoreboard doesn't recognize itself falls through to PAPI - this is how
 * `%vault_eco_balance_formatted%`, `%jobs_job%`, etc. from other plugins work in board/leaderboard
 * lines without RLScoreboard depending on those plugins directly (section 7).
 */
class PlaceholderAPIIntegration(private val plugin: RLScoreboardPlugin) : AbstractIntegration() {
    override val id = "placeholderapi"
    override val pluginName = "PlaceholderAPI"
    override val minSupportedVersion = "2.11"
    override val maxTestedVersion = "2.11"
    override val capabilities = setOf("placeholders")

    override fun enable() {
        RLPlaceholderExpansion(plugin).register()
        plugin.placeholderEngine.placeholderApiBridge = { player, text -> PlaceholderAPI.setPlaceholders(player, text) }
    }
}
