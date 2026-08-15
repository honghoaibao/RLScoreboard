package dev.rlscoreboard.integration.placeholderapi

import dev.rlscoreboard.RLScoreboardPlugin
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

/** Exposes RLScoreboard's own `%rl_*%` placeholders to every other plugin as `%rlscoreboard_*%` via PlaceholderAPI. */
class RLPlaceholderExpansion(private val plugin: RLScoreboardPlugin) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "rlscoreboard"
    override fun getAuthor(): String = plugin.pluginMeta.authors.joinToString(", ").ifEmpty { "RLScoreboard" }
    override fun getVersion(): String = plugin.pluginMeta.version
    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? =
        plugin.placeholderEngine.resolveSingle(params, player)
}
