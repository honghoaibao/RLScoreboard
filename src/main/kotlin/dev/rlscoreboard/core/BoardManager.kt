package dev.rlscoreboard.core

import dev.rlscoreboard.api.model.BoardDefinition
import dev.rlscoreboard.condition.ConditionEngine
import dev.rlscoreboard.config.BoardConfigLoader
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns every known board - the ones loaded from scoreboards/*.yml plus any "synthetic"
 * boards published by other engines at runtime (currently: SIDEBAR-type leaderboards,
 * see [dev.rlscoreboard.leaderboard.renderer.SidebarLeaderboardRenderer]) - and picks
 * which one each player should currently see.
 */
class BoardManager(
    private val plugin: Plugin,
    private val loader: BoardConfigLoader,
    private val conditions: ConditionEngine
) {
    private val boards = ConcurrentHashMap<String, BoardDefinition>()
    private val synthetic = ConcurrentHashMap<String, BoardDefinition>()
    private val forcedBoard = ConcurrentHashMap<UUID, String>()

    fun reload() {
        boards.clear()
        boards.putAll(loader.loadAll())
        plugin.logger.info("Loaded ${boards.size} scoreboard(s).")
    }

    fun registerSynthetic(board: BoardDefinition) {
        synthetic[board.id] = board
    }

    fun unregisterSynthetic(id: String) {
        synthetic.remove(id)
    }

    fun all(): Collection<BoardDefinition> = boards.values + synthetic.values

    fun get(id: String): BoardDefinition? = boards[id] ?: synthetic[id]

    fun forceBoard(player: Player, boardId: String?) {
        if (boardId == null) forcedBoard.remove(player.uniqueId) else forcedBoard[player.uniqueId] = boardId
    }

    fun activeBoardId(player: Player): String? = resolveBoardFor(player)?.id

    /**
     * Picks the highest-priority enabled board whose conditions currently pass for this
     * player, falling back to a board marked `fallback: true` if none match (section 6).
     */
    fun resolveBoardFor(player: Player): BoardDefinition? {
        forcedBoard[player.uniqueId]?.let { forcedId -> get(forcedId)?.let { return it } }

        var fallback: BoardDefinition? = null
        var best: BoardDefinition? = null
        for (board in all()) {
            if (!board.enabled) continue
            if (board.fallback && fallback == null) fallback = board
            if (conditions.evaluate(board.conditions, player)) {
                if (best == null || board.priority > best!!.priority) best = board
            }
        }
        return best ?: fallback
    }
}
