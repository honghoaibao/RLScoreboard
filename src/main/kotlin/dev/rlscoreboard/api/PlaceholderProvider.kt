package dev.rlscoreboard.api

import org.bukkit.entity.Player

/**
 * Resolves a single internal placeholder identifier (without the surrounding `%` signs)
 * to a value for a given player. Third-party plugins register providers through
 * [RLScoreboardAPI.registerPlaceholder] instead of needing to depend on PlaceholderAPI.
 * [player] is null when resolving in a context with no associated player.
 */
fun interface PlaceholderProvider {
    fun resolve(player: Player?): String
}
