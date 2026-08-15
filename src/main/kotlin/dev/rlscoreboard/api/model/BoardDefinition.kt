package dev.rlscoreboard.api.model

import dev.rlscoreboard.condition.ConditionSet

/**
 * Immutable definition of a single scoreboard, parsed from a YAML file under scoreboards/
 * (or published at runtime by another engine, e.g. a SIDEBAR-type leaderboard - see
 * [dev.rlscoreboard.core.BoardManager.registerSynthetic]). Purely data: rendering,
 * animation, and condition evaluation all live in dev.rlscoreboard.core.
 */
data class BoardDefinition(
    val id: String,
    val enabled: Boolean,
    val priority: Int,
    val title: AnimatedText,
    val lines: List<BoardLine>,
    val updateIntervalTicks: Long,
    val conditions: ConditionSet,
    val fallback: Boolean
)

data class BoardLine(
    val text: AnimatedText,
    val conditions: ConditionSet
)
