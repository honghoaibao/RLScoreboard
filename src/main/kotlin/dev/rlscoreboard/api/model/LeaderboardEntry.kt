package dev.rlscoreboard.api.model

import java.util.UUID

/**
 * One ranked row. [value] drives sorting; [formattedValue] is what actually gets
 * substituted into a leaderboard's `format:` lines via `%value%` and defaults to a
 * sensible number format, but data sources are free to override it (e.g. "12h 30m"
 * for playtime instead of a raw tick count).
 */
data class LeaderboardEntry(
    val playerId: UUID?,
    val displayName: String,
    val value: Double,
    val formattedValue: String = defaultFormat(value)
) : Comparable<LeaderboardEntry> {

    // Highest value first.
    override fun compareTo(other: LeaderboardEntry): Int = other.value.compareTo(this.value)

    companion object {
        private fun defaultFormat(value: Double): String =
            if (!value.isInfinite() && value == Math.floor(value)) value.toLong().toString()
            else "%.2f".format(value)
    }
}
