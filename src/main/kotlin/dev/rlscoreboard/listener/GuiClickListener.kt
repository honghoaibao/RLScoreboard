package dev.rlscoreboard.listener

import dev.rlscoreboard.leaderboard.renderer.GuiLeaderboardRenderer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

/** Prevents players from taking/rearranging items in a GUI-type leaderboard - see [GuiLeaderboardRenderer]. */
class GuiClickListener : Listener {
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.inventory.holder is GuiLeaderboardRenderer.GuiHolder) {
            event.isCancelled = true
        }
    }
}
