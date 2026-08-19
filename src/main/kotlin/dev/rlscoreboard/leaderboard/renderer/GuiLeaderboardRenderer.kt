package dev.rlscoreboard.leaderboard.renderer

import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardEntry
import dev.rlscoreboard.config.LocaleManager
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
class GuiLeaderboardRenderer(private val localeManager: LocaleManager) : LeaderboardRenderer {
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
            val icon = definition.topIcons[position] ?: DefaultRankIcon.forPosition(position)
            inventory.setItem(index, buildItem(entry, icon, position))
        }
        if (entries.isEmpty()) {
            inventory.setItem(size / 2, buildEmptyStateItem())
        } else if (size > entries.size) {
            inventory.setItem(size - 1, buildFooterItem(entries.size))
        }
        return inventory
    }

    private fun buildFooterItem(count: Int): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item
        meta.displayName(ColorUtil.toComponent(localeManager.get("leaderboard_gui_footer", "count" to count.toString())))
        item.itemMeta = meta
        return item
    }

    private fun buildEmptyStateItem(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item
        meta.displayName(ColorUtil.toComponent(localeManager.get("leaderboard_empty")))
        item.itemMeta = meta
        return item
    }

    private fun buildItem(entry: LeaderboardEntry, icon: String, position: Int): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item

        entry.playerId?.let { uuid -> meta.owningPlayer = Bukkit.getOfflinePlayer(uuid) }
        // Top 3 get a bold name so they read as more prominent than the rest of the list,
        // in addition to their medal icon (section 11 - "top 3 nổi bật").
        val nameStyle = if (position <= 3) "&l" else ""
        meta.displayName(ColorUtil.toComponent("$icon $nameStyle&f${entry.displayName}"))
        meta.lore(listOf(ColorUtil.toComponent(localeManager.get("leaderboard_gui_value", "value" to entry.formattedValue))))

        item.itemMeta = meta
        return item
    }

    /** Marks an inventory as an RLScoreboard leaderboard GUI so [dev.rlscoreboard.listener.GuiClickListener] knows to cancel clicks in it. */
    class GuiHolder(val leaderboardId: String) : InventoryHolder {
        lateinit var inventoryRef: Inventory
        override fun getInventory(): Inventory = inventoryRef
    }
}
