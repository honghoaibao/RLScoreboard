package dev.rlscoreboard.leaderboard

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
    boardManager: BoardManager
) {
    init {
        manager.registerRenderer(SidebarLeaderboardRenderer(boardManager))
        manager.registerRenderer(HologramLeaderboardRenderer())
        manager.registerRenderer(TabLeaderboardRenderer())
        manager.registerRenderer(NpcLeaderboardRenderer())
        manager.registerRenderer(GuiLeaderboardRenderer())
    }

    fun tick(nowMillis: Long) = manager.tick(nowMillis)
}
