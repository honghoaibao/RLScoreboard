package dev.rlscoreboard.leaderboard.renderer

import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardEntry
import dev.rlscoreboard.util.ColorUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.concurrent.ConcurrentHashMap

/**
 * Unlike SIDEBAR/HOLOGRAM/TAB/NPC (all "ambient", always visible), a GUI leaderboard is a
 * snapshot a player opens on demand via `/rlscoreboard leaderboard view <id>` (see
 * RLScoreboardCommand + GuiClickListener, which cancels clicks in it). [render] just
 * refreshes the cached snapshot on the normal leaderboard tick; [open] builds a fresh
 * inventory from whatever that latest snapshot was.
 */
class GuiLeaderboardRenderer : LeaderboardRenderer {
    override val type = "GUI"

    private val snapshots = ConcurrentHashMap<String, Pair<LeaderboardDefinition, List<LeaderboardEntry>>>()

    override fun render(definition: LeaderboardDefinition, entries: List<LeaderboardEntry>) {
        snapshots[definition.id] = definition to entries
    }

    override fun remove(definition: LeaderboardDefinition) {
        snapshots.remove(definition.id)
    }

    /** Null if this leaderboard has no snapshot yet (wrong type, or hasn't refreshed once). */
    fun open(leaderboardId: String): Inventory? {
        val (definition, entries) = snapshots[leaderboardId] ?: return null
        val size = (((entries.size.coerceAtLeast(1) + 8) / 9) * 9).coerceIn(9, 54)

        val holder = GuiHolder(leaderboardId)
        val title = ColorUtil.toComponent(definition.title.firstOrNull() ?: definition.id)
        val inventory = Bukkit.createInventory(holder, size, title)
        holder.inventoryRef = inventory

        entries.forEachIndexed { index, entry ->
            if (index >= size) return@forEachIndexed
            val position = index + 1
            val icon = definition.topIcons[position] ?: "&7#$position"
            inventory.setItem(index, buildItem(entry, icon))
        }
        return inventory
    }

    private fun buildItem(entry: LeaderboardEntry, icon: String): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item

        entry.playerId?.let { uuid -> meta.owningPlayer = Bukkit.getOfflinePlayer(uuid) }
        meta.displayName(ColorUtil.toComponent("$icon &f${entry.displayName}"))
        meta.lore(listOf(ColorUtil.toComponent("&7Value: &f${entry.formattedValue}")))

        item.itemMeta = meta
        return item
    }

    /** Marks an inventory as an RLScoreboard leaderboard GUI so [dev.rlscoreboard.listener.GuiClickListener] knows to cancel clicks in it. */
    class GuiHolder(val leaderboardId: String) : InventoryHolder {
        lateinit var inventoryRef: Inventory
        override fun getInventory(): Inventory = inventoryRef
    }
}
