package dev.rlscoreboard.leaderboard.renderer

/**
 * Default icon for a leaderboard position when a leaderboard's config doesn't define its own
 * `topIcons`. Shared by every renderer type (SIDEBAR/HOLOGRAM/TAB/NPC/GUI) so "top 3 stands
 * out" looks and behaves the same everywhere out of the box, not just wherever a leaderboard
 * happens to have custom icons configured.
 */
internal object DefaultRankIcon {
    fun forPosition(position: Int): String = when (position) {
        1 -> "&6🥇"
        2 -> "&7🥈"
        3 -> "&c🥉"
        else -> "&7#$position"
    }
}
