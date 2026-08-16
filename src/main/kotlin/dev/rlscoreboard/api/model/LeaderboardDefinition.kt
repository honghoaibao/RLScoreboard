package dev.rlscoreboard.api.model

import dev.rlscoreboard.condition.ConditionSet

data class LeaderboardLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
)

/**
 * Immutable definition of a single leaderboard, parsed from YAML under leaderboards/.
 * [priority]/[conditions] only matter for SIDEBAR-type leaderboards, which compete for
 * screen space with every other board through the normal [dev.rlscoreboard.core.BoardManager]
 * selection instead of RLScoreboard maintaining a second, parallel sidebar system.
 */
data class LeaderboardDefinition(
    val id: String,
    val enabled: Boolean,
    val displayType: String,
    val title: List<String>,
    val dataSourceId: String,
    val dataSourceArgs: Map<String, String>,
    val entries: Int,
    val updateIntervalTicks: Long,
    val entryFormat: List<String>,
    val topIcons: Map<Int, String>,
    val location: LeaderboardLocation?,
    val priority: Int = 0,
    val conditions: ConditionSet = ConditionSet.EMPTY
)
