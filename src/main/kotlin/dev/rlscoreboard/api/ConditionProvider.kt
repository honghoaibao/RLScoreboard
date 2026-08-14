package dev.rlscoreboard.api

import org.bukkit.entity.Player

/**
 * A custom condition type registered by another plugin, referenced from YAML as:
 * ```
 * conditions:
 *   custom:
 *     provider: myplugin_condition_id
 *     args: { key: value }
 * ```
 */
fun interface ConditionProvider {
    fun evaluate(player: Player, args: Map<String, String>): Boolean
}
