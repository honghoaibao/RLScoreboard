package dev.rlscoreboard.api

import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardEntry

/**
 * Renders ranked entries somewhere - sidebar, hologram, NPC, GUI, TAB list, etc.
 * RLScoreboard ships SIDEBAR and HOLOGRAM; register more display types with
 * [RLScoreboardAPI.registerLeaderboardRenderer]. [type] is matched case-insensitively
 * against a leaderboard's `display.type` YAML value.
 */
interface LeaderboardRenderer {
    val type: String

    fun render(definition: LeaderboardDefinition, entries: List<LeaderboardEntry>)

    /** Called when a leaderboard using this renderer is deleted, disabled, or reloaded away. */
    fun remove(definition: LeaderboardDefinition)
}
