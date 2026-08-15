package dev.rlscoreboard.core

import dev.rlscoreboard.animation.AnimationEngine
import dev.rlscoreboard.condition.ConditionEngine
import dev.rlscoreboard.condition.ConditionSet
import dev.rlscoreboard.placeholder.PlaceholderEngine
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * Top-level orchestrator for the scoreboard side of the plugin. Owns the sub-managers and
 * exposes the single [tick] entry point that [UpdateManager]'s heartbeat calls per player.
 */
class ScoreboardEngine(
    val boardManager: BoardManager,
    placeholders: PlaceholderEngine,
    conditions: ConditionEngine,
    animations: AnimationEngine,
    private val sessions: PlayerSessionManager
) {
    private val lineRenderer = LineRenderer(placeholders, conditions, animations)
    private val boardRenderer = BoardRenderer(sessions)

    // Per player+board "last updated at" so a 30s-interval board isn't recomputed on every heartbeat tick.
    private val lastUpdateMillis = ConcurrentHashMap<String, Long>()

    fun tick(player: Player, nowMillis: Long) {
        val board = boardManager.resolveBoardFor(player) ?: run {
            sessions.remove(player)
            return
        }

        val key = "${player.uniqueId}:${board.id}"
        val last = lastUpdateMillis[key] ?: 0L
        val intervalMillis = board.updateIntervalTicks * 50L
        if (nowMillis - last < intervalMillis) return
        lastUpdateMillis[key] = nowMillis

        val title = lineRenderer.render(board.title, ConditionSet.EMPTY, player) ?: return
        val lines = board.lines.mapNotNull { line -> lineRenderer.render(line.text, line.conditions, player) }
        boardRenderer.render(player, board.id, title, lines)
    }

    fun clearFor(player: Player) {
        sessions.remove(player)
    }
}
