package dev.rlscoreboard.api

import dev.rlscoreboard.api.model.BoardDefinition
import org.bukkit.entity.Player

interface BoardAPI {
    fun getBoard(id: String): BoardDefinition?
    fun getBoards(): Collection<BoardDefinition>
    fun reload()

    /** The board currently being shown to [player] (after priority/condition resolution). */
    fun getActiveBoardId(player: Player): String?

    /** Pins a player to a specific board regardless of priority/conditions, or clears the pin with null. */
    fun forceBoard(player: Player, boardId: String?)

    /** Forces an immediate re-render for one player instead of waiting for the next heartbeat. */
    fun refresh(player: Player)
}
