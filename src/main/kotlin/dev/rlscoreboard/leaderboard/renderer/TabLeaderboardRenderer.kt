package dev.rlscoreboard.leaderboard.renderer

import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardEntry
import dev.rlscoreboard.config.LocaleManager
import dev.rlscoreboard.util.ColorUtil
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit

/**
 * Renders a leaderboard into every online player's tab list footer. Whole-server display,
 * unlike SIDEBAR/HOLOGRAM/NPC which can be player- or location-scoped - there's only one
 * footer per player, so if more than one TAB-type leaderboard is enabled at once, whichever
 * renders last on a given tick wins. Keep to at most one TAB leaderboard per server.
 */
class TabLeaderboardRenderer(private val localeManager: LocaleManager) : LeaderboardRenderer {
    override val type = "TAB"

    override fun render(definition: LeaderboardDefinition, entries: List<LeaderboardEntry>) {
        val lines = mutableListOf<String>()
        lines += definition.title
        if (entries.isEmpty()) lines += localeManager.get("leaderboard_empty")
        entries.forEachIndexed { index, entry ->
            val position = index + 1
            val icon = definition.topIcons[position] ?: DefaultRankIcon.forPosition(position)
            for (formatLine in definition.entryFormat) {
                lines += formatLine
                    .replace("%position%", icon)
                    .replace("%player%", entry.displayName)
                    .replace("%value%", entry.formattedValue)
            }
        }

        val footer = ColorUtil.toComponent(lines.joinToString("\n"))
        for (player in Bukkit.getOnlinePlayers()) {
            player.sendPlayerListFooter(footer)
        }
    }

    override fun remove(definition: LeaderboardDefinition) {
        for (player in Bukkit.getOnlinePlayers()) {
            player.sendPlayerListFooter(Component.empty())
        }
    }
}
