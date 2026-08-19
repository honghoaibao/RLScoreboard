package dev.rlscoreboard.leaderboard.renderer

import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardEntry
import dev.rlscoreboard.config.LocaleManager
import dev.rlscoreboard.util.ColorUtil
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders a leaderboard as a stack of [TextDisplay] entities anchored at its configured
 * location - one entity per line, spaced vertically. Uses entities (the natively supported,
 * non-deprecated way to show floating text on modern Paper) rather than the old invisible-
 * armor-stand hologram hack. Entities are non-persistent by design and get respawned from
 * scratch on the next render after a restart, which avoids ever accumulating orphaned
 * duplicates across restarts (section 24 - graceful failure over silent breakage).
 */
class HologramLeaderboardRenderer(private val localeManager: LocaleManager) : LeaderboardRenderer {
    override val type = "HOLOGRAM"

    private val active = ConcurrentHashMap<String, MutableList<TextDisplay>>()
    private val lineSpacing = 0.28

    override fun render(definition: LeaderboardDefinition, entries: List<LeaderboardEntry>) {
        val location = definition.location ?: return
        val world = Bukkit.getWorld(location.world) ?: return
        val base = Location(world, location.x, location.y, location.z, location.yaw, location.pitch)

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

        val displays = active.getOrPut(definition.id) { mutableListOf() }
        while (displays.size < lines.size) {
            val spawnLoc = base.clone().add(0.0, -lineSpacing * displays.size, 0.0)
            val display = world.spawn(spawnLoc, TextDisplay::class.java) { td ->
                td.billboard = Display.Billboard.CENTER
                td.isPersistent = false
                td.setGravity(false)
            }
            displays += display
        }
        while (displays.size > lines.size) {
            displays.removeAt(displays.size - 1).remove()
        }

        lines.forEachIndexed { index, line ->
            val display = displays[index]
            display.teleport(base.clone().add(0.0, -lineSpacing * index, 0.0))
            display.text(ColorUtil.toComponent(line))
        }
    }

    override fun remove(definition: LeaderboardDefinition) {
        active.remove(definition.id)?.forEach { it.remove() }
    }
}
