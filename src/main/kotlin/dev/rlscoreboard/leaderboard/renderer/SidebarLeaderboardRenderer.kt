package dev.rlscoreboard.leaderboard.renderer

import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.model.AnimatedText
import dev.rlscoreboard.api.model.BoardDefinition
import dev.rlscoreboard.api.model.BoardLine
import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardEntry
import dev.rlscoreboard.condition.ConditionSet
import dev.rlscoreboard.config.LocaleManager
import dev.rlscoreboard.core.BoardManager

/**
 * Publishes a leaderboard as a normal sidebar board so it competes on priority/conditions
 * with every other board through the existing [BoardManager] instead of RLScoreboard
 * needing a second, parallel sidebar system just for leaderboards.
 */
class SidebarLeaderboardRenderer(
    private val boardManager: BoardManager,
    private val localeManager: LocaleManager
) : LeaderboardRenderer {
    override val type = "SIDEBAR"

    override fun render(definition: LeaderboardDefinition, entries: List<LeaderboardEntry>) {
        val lines = mutableListOf<BoardLine>()
        if (entries.isEmpty()) {
            lines += BoardLine(AnimatedText.static(localeManager.get("leaderboard_empty")), ConditionSet.EMPTY)
        }
        entries.forEachIndexed { index, entry ->
            val position = index + 1
            val icon = definition.topIcons[position] ?: DefaultRankIcon.forPosition(position)
            for (formatLine in definition.entryFormat) {
                val resolved = formatLine
                    .replace("%position%", icon)
                    .replace("%player%", entry.displayName)
                    .replace("%value%", entry.formattedValue)
                lines += BoardLine(AnimatedText.static(resolved), ConditionSet.EMPTY)
            }
        }

        val board = BoardDefinition(
            id = boardId(definition.id),
            enabled = definition.enabled,
            priority = definition.priority,
            title = AnimatedText.static(definition.title.firstOrNull() ?: definition.id),
            lines = lines,
            updateIntervalTicks = definition.updateIntervalTicks,
            conditions = definition.conditions,
            fallback = false
        )
        boardManager.registerSynthetic(board)
    }

    override fun remove(definition: LeaderboardDefinition) {
        boardManager.unregisterSynthetic(boardId(definition.id))
    }

    private fun boardId(leaderboardId: String) = "leaderboard:$leaderboardId"
}
