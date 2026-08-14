package dev.rlscoreboard.api.internal

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.BoardAPI
import dev.rlscoreboard.api.ConditionProvider
import dev.rlscoreboard.api.LeaderboardAPI
import dev.rlscoreboard.api.LeaderboardDataSource
import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.api.RLScoreboardAPI
import org.bukkit.entity.Player

class RLScoreboardAPIImpl(private val plugin: RLScoreboardPlugin) : RLScoreboardAPI {

    override fun registerPlaceholder(identifier: String, provider: PlaceholderProvider) =
        plugin.placeholderEngine.register(identifier, provider)

    override fun unregisterPlaceholder(identifier: String) =
        plugin.placeholderEngine.unregister(identifier)

    override fun registerConditionProvider(id: String, provider: ConditionProvider) =
        plugin.conditionEngine.registerProvider(id, provider)

    override fun registerDataSource(dataSource: LeaderboardDataSource) =
        plugin.leaderboardEngine.dataSources.register(dataSource)

    override fun unregisterDataSource(id: String) =
        plugin.leaderboardEngine.dataSources.unregister(id)

    override fun registerLeaderboardRenderer(renderer: LeaderboardRenderer) =
        plugin.leaderboardEngine.manager.registerRenderer(renderer)

    override fun board(): BoardAPI = object : BoardAPI {
        override fun getBoard(id: String) = plugin.boardManager.get(id)
        override fun getBoards() = plugin.boardManager.all()
        override fun reload() = plugin.boardManager.reload()
        override fun getActiveBoardId(player: Player) = plugin.boardManager.activeBoardId(player)
        override fun forceBoard(player: Player, boardId: String?) = plugin.boardManager.forceBoard(player, boardId)
        override fun refresh(player: Player) = plugin.scoreboardEngine.tick(player, System.currentTimeMillis())
    }

    override fun leaderboard(): LeaderboardAPI = object : LeaderboardAPI {
        override fun getLeaderboard(id: String) = plugin.leaderboardEngine.manager.get(id)
        override fun getLeaderboards() = plugin.leaderboardEngine.manager.all()
        override fun create(id: String, displayType: String, dataSourceId: String) =
            plugin.leaderboardEngine.manager.create(id, displayType, dataSourceId)
        override fun delete(id: String) = plugin.leaderboardEngine.manager.delete(id)
        override fun reload() = plugin.leaderboardEngine.manager.reload()
        override fun forceRefresh(id: String) = plugin.leaderboardEngine.manager.forceRefresh(id)
    }
}
