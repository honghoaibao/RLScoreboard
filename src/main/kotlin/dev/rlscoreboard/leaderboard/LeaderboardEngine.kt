package dev.rlscoreboard.leaderboard

import dev.rlscoreboard.config.LocaleManager
import dev.rlscoreboard.core.BoardManager
import dev.rlscoreboard.leaderboard.renderer.GuiLeaderboardRenderer
import dev.rlscoreboard.leaderboard.renderer.HologramLeaderboardRenderer
import dev.rlscoreboard.leaderboard.renderer.NpcLeaderboardRenderer
import dev.rlscoreboard.leaderboard.renderer.SidebarLeaderboardRenderer
import dev.rlscoreboard.leaderboard.renderer.TabLeaderboardRenderer

/** Top-level orchestrator for the leaderboard side of the plugin - the leaderboard-package equivalent of ScoreboardEngine. */
class LeaderboardEngine(
    val dataSources: DataSourceManager,
    val ranking: RankingEngine,
    val manager: LeaderboardManager,
    boardManager: BoardManager,
    localeManager: LocaleManager
) {
    init {
        manager.registerRenderer(SidebarLeaderboardRenderer(boardManager, localeManager))
        manager.registerRenderer(HologramLeaderboardRenderer(localeManager))
        manager.registerRenderer(TabLeaderboardRenderer(localeManager))
        manager.registerRenderer(NpcLeaderboardRenderer(localeManager))
        manager.registerRenderer(GuiLeaderboardRenderer(localeManager))
    }

    fun tick(nowMillis: Long) = manager.tick(nowMillis)
}
