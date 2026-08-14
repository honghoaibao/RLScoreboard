package dev.rlscoreboard.leaderboard.renderer

import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardEntry
import dev.rlscoreboard.util.ColorUtil
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.entity.Villager
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders a leaderboard as a stationary, AI-disabled [Villager] at the configured location
 * with the #1 entry as its nameplate, plus a small [TextDisplay] stack floating above it for
 * the rest - same line-rendering technique as [HologramLeaderboardRenderer], just with a
 * physical body standing at the spot. Deliberately doesn't attempt a fake human/skin NPC:
 * that needs either raw packet manipulation or a Citizens dependency, and this plugin adds
 * neither (see README) - a plain mob entity needs nothing beyond stock Paper API.
 */
class NpcLeaderboardRenderer : LeaderboardRenderer {
    override val type = "NPC"

    private val activeNpc = ConcurrentHashMap<String, Villager>()
    private val activeText = ConcurrentHashMap<String, MutableList<TextDisplay>>()
    private val lineSpacing = 0.28
    private val textHeightAboveHead = 2.1

    override fun render(definition: LeaderboardDefinition, entries: List<LeaderboardEntry>) {
        val location = definition.location ?: return
        val world = Bukkit.getWorld(location.world) ?: return
        val base = Location(world, location.x, location.y, location.z, location.yaw, location.pitch)

        val top = entries.firstOrNull()
        val npcName = if (top != null) {
            val icon = definition.topIcons[1] ?: "&6🥇"
            "$icon &f${top.displayName} &7- &f${top.formattedValue}"
        } else {
            definition.title.firstOrNull() ?: definition.id
        }

        val npc = activeNpc.getOrPut(definition.id) { spawnNpc(world, base) }
        npc.teleport(base)
        npc.customName(ColorUtil.toComponent(npcName))

        val extraLines = mutableListOf<String>()
        extraLines += definition.title
        entries.drop(1).forEachIndexed { index, entry ->
            val position = index + 2
            val icon = definition.topIcons[position] ?: "&7#$position"
            for (formatLine in definition.entryFormat) {
                extraLines += formatLine
                    .replace("%position%", icon)
                    .replace("%player%", entry.displayName)
                    .replace("%value%", entry.formattedValue)
            }
        }
        renderTextStack(definition.id, world, base.clone().add(0.0, textHeightAboveHead, 0.0), extraLines)
    }

    private fun renderTextStack(id: String, world: World, above: Location, lines: List<String>) {
        val displays = activeText.getOrPut(id) { mutableListOf() }
        while (displays.size < lines.size) {
            val spawnLoc = above.clone().add(0.0, -lineSpacing * displays.size, 0.0)
            displays += world.spawn(spawnLoc, TextDisplay::class.java) { td ->
                td.billboard = Display.Billboard.CENTER
                td.isPersistent = false
                td.setGravity(false)
            }
        }
        while (displays.size > lines.size) {
            displays.removeAt(displays.size - 1).remove()
        }
        lines.forEachIndexed { index, line ->
            val display = displays[index]
            display.teleport(above.clone().add(0.0, -lineSpacing * index, 0.0))
            display.text(ColorUtil.toComponent(line))
        }
    }

    private fun spawnNpc(world: World, location: Location): Villager =
        world.spawn(location, Villager::class.java) { npc ->
            npc.setAI(false)
            npc.isInvulnerable = true
            npc.isSilent = true
            npc.isPersistent = false
            npc.setCanPickupItems(false)
            npc.isCustomNameVisible = true
            npc.setGravity(false)
            npc.isCollidable = false
        }

    override fun remove(definition: LeaderboardDefinition) {
        activeNpc.remove(definition.id)?.remove()
        activeText.remove(definition.id)?.forEach { it.remove() }
    }
}
