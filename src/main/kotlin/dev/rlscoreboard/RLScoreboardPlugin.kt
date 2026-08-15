package dev.rlscoreboard

import dev.rlscoreboard.animation.AnimationEngine
import dev.rlscoreboard.api.RLScoreboardAPI
import dev.rlscoreboard.api.internal.RLScoreboardAPIImpl
import dev.rlscoreboard.command.RLScoreboardCommand
import dev.rlscoreboard.condition.ConditionEngine
import dev.rlscoreboard.config.BoardConfigLoader
import dev.rlscoreboard.config.ConfigManager
import dev.rlscoreboard.config.LeaderboardConfigLoader
import dev.rlscoreboard.config.LocaleManager
import dev.rlscoreboard.core.BoardManager
import dev.rlscoreboard.core.PlayerSessionManager
import dev.rlscoreboard.core.ScoreboardEngine
import dev.rlscoreboard.core.UpdateManager
import dev.rlscoreboard.integration.IntegrationManager
import dev.rlscoreboard.integration.auraskills.AuraSkillsIntegration
import dev.rlscoreboard.integration.jobs.JobsIntegration
import dev.rlscoreboard.integration.luckperms.LuckPermsIntegration
import dev.rlscoreboard.integration.placeholderapi.PlaceholderAPIIntegration
import dev.rlscoreboard.integration.vault.VaultIntegration
import dev.rlscoreboard.leaderboard.DataSourceManager
import dev.rlscoreboard.leaderboard.LeaderboardEngine
import dev.rlscoreboard.leaderboard.LeaderboardManager
import dev.rlscoreboard.leaderboard.RankingEngine
import dev.rlscoreboard.leaderboard.datasource.PersistentStatDataSource
import dev.rlscoreboard.leaderboard.datasource.StatisticDataSource
import dev.rlscoreboard.listener.GuiClickListener
import dev.rlscoreboard.listener.PlayerConnectionListener
import dev.rlscoreboard.placeholder.InternalPlaceholders
import dev.rlscoreboard.placeholder.PlaceholderEngine
import dev.rlscoreboard.storage.LeaderboardHistoryService
import dev.rlscoreboard.storage.StatsSyncService
import dev.rlscoreboard.storage.sql.Database
import dev.rlscoreboard.storage.sql.DatabaseType
import dev.rlscoreboard.storage.sql.LeaderboardHistoryRepository
import dev.rlscoreboard.storage.sql.PlayerStatsRepository
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Statistic
import org.bukkit.plugin.java.JavaPlugin

class RLScoreboardPlugin : JavaPlugin() {

    lateinit var configManager: ConfigManager
        private set
    lateinit var localeManager: LocaleManager
        private set
    lateinit var placeholderEngine: PlaceholderEngine
        private set
    lateinit var conditionEngine: ConditionEngine
        private set
    lateinit var animationEngine: AnimationEngine
        private set
    lateinit var sessionManager: PlayerSessionManager
        private set
    lateinit var boardManager: BoardManager
        private set
    lateinit var scoreboardEngine: ScoreboardEngine
        private set
    lateinit var leaderboardEngine: LeaderboardEngine
        private set
    lateinit var integrationManager: IntegrationManager
        private set

    /** Non-null only when `storage.enabled: true` and the connection succeeded - see [initStorage]. */
    var statsSyncService: StatsSyncService? = null
        private set

    /** Non-null only when `storage.enabled: true` and the connection succeeded - backs `/rlscoreboard leaderboard history`. */
    var leaderboardHistoryRepository: LeaderboardHistoryRepository? = null
        private set

    private lateinit var updateManager: UpdateManager
    private var database: Database? = null
    private var persistentDataSources: List<PersistentStatDataSource> = emptyList()
    private var leaderboardHistoryService: LeaderboardHistoryService? = null

    override fun onEnable() {
        configManager = ConfigManager(this)
        configManager.loadAll()

        localeManager = LocaleManager(this, configManager)
        localeManager.load()

        placeholderEngine = PlaceholderEngine()
        InternalPlaceholders.registerAll(placeholderEngine)

        conditionEngine = ConditionEngine(placeholderEngine)
        animationEngine = AnimationEngine()
        sessionManager = PlayerSessionManager()

        val boardLoader = BoardConfigLoader(this, configManager.scoreboardsFolder)
        boardManager = BoardManager(this, boardLoader, conditionEngine)
        boardManager.reload()

        scoreboardEngine = ScoreboardEngine(boardManager, placeholderEngine, conditionEngine, animationEngine, sessionManager)

        val dataSources = DataSourceManager()
        dataSources.register(StatisticDataSource("topkills", Statistic.PLAYER_KILLS))
        dataSources.register(StatisticDataSource("topdeaths", Statistic.DEATHS))
        dataSources.register(StatisticDataSource("topplaytime", Statistic.PLAY_ONE_MINUTE))

        val leaderboardLoader = LeaderboardConfigLoader(this, configManager.leaderboardsFolder)
        val ranking = RankingEngine()
        val leaderboardManager = LeaderboardManager(this, leaderboardLoader, dataSources, ranking)
        leaderboardEngine = LeaderboardEngine(dataSources, ranking, leaderboardManager, boardManager, localeManager)
        leaderboardManager.reload()

        if (configManager.storageEnabled()) {
            initStorage(dataSources, leaderboardManager, ranking)
        }

        RLScoreboardAPI.register(RLScoreboardAPIImpl(this))

        integrationManager = IntegrationManager(this)
        integrationManager.loadAll(
            listOf(
                PlaceholderAPIIntegration(this),
                VaultIntegration(this),
                JobsIntegration(),
                AuraSkillsIntegration(this),
                LuckPermsIntegration(this)
            )
        )

        server.pluginManager.registerEvents(PlayerConnectionListener(this), this)
        server.pluginManager.registerEvents(GuiClickListener(), this)

        getCommand("rlscoreboard")?.let { cmd ->
            val executor = RLScoreboardCommand(this)
            cmd.setExecutor(executor)
            cmd.tabCompleter = executor
        }

        updateManager = UpdateManager(this, scoreboardEngine, leaderboardEngine, configManager.heartbeatTicks())
        updateManager.start()

        logger.info("RLScoreboard enabled - ${boardManager.all().size} board(s), ${leaderboardEngine.manager.all().size} leaderboard(s).")
    }

    override fun onDisable() {
        if (this::updateManager.isInitialized) updateManager.stop()
        statsSyncService?.stop()
        leaderboardHistoryService?.stop()
        persistentDataSources.forEach { it.stop() }
        database?.close()
        RLScoreboardAPI.unregister()

        val mainScoreboard = server.scoreboardManager?.mainScoreboard
        if (mainScoreboard != null) {
            server.onlinePlayers.forEach { it.scoreboard = mainScoreboard }
        }
    }

    fun reloadEverything() {
        configManager.reload()
        localeManager.reload()
        boardManager.reload()
        leaderboardEngine.manager.reload()
    }

    /**
     * Wires up storage (section 18) - a pooled JDBC connection, the `rlscoreboard_player_stats`
     * and `rlscoreboard_leaderboard_history` tables, [StatsSyncService]/[LeaderboardHistoryService]
     * keeping them fresh, and the four "*_alltime" datasources that read from the former.
     * Any failure here (bad config, can't connect, schema error) is caught and logged -
     * RLScoreboard keeps running with only the online-only datasources rather than failing
     * to start (section 24: graceful fallback, not a crash).
     */
    private fun initStorage(dataSources: DataSourceManager, leaderboardManager: LeaderboardManager, ranking: RankingEngine) {
        val db = buildDatabase()
        runCatching { db.connect() }.onFailure {
            logger.warning("Failed to connect to storage (${it.message}) - offline-inclusive leaderboards will be unavailable.")
            return
        }
        database = db

        val statsRepository = PlayerStatsRepository(db, logger)
        runCatching { statsRepository.initSchema() }.onFailure {
            logger.warning("Failed to initialize storage schema (${it.message}) - offline-inclusive leaderboards will be unavailable.")
            database = null
            db.close()
            return
        }

        val trackedStatistics = mapOf(
            "topkills" to Statistic.PLAYER_KILLS,
            "topdeaths" to Statistic.DEATHS,
            "topplaytime" to Statistic.PLAY_ONE_MINUTE
        )
        val sync = StatsSyncService(this, statsRepository, trackedStatistics) {
            Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
        }
        sync.start(configManager.storageSyncTicks())
        statsSyncService = sync

        val refreshTicks = configManager.storageRefreshTicks()
        val persistent = listOf(
            PersistentStatDataSource(this, "topkills_alltime", "topkills", statsRepository, refreshTicks),
            PersistentStatDataSource(this, "topdeaths_alltime", "topdeaths", statsRepository, refreshTicks),
            PersistentStatDataSource(this, "topplaytime_alltime", "topplaytime", statsRepository, refreshTicks),
            PersistentStatDataSource(this, "economy_alltime", "economy", statsRepository, refreshTicks)
        )
        persistent.forEach { source ->
            source.start()
            dataSources.register(source)
        }
        persistentDataSources = persistent

        val historyRepository = LeaderboardHistoryRepository(db, logger)
        runCatching { historyRepository.initSchema() }
            .onSuccess {
                leaderboardHistoryRepository = historyRepository
                val history = LeaderboardHistoryService(this, leaderboardManager, ranking, historyRepository)
                history.start(configManager.storageHistoryTicks())
                leaderboardHistoryService = history
            }
            .onFailure { logger.warning("Failed to initialize leaderboard history schema (${it.message}) - '/rlscoreboard leaderboard history' will be unavailable.") }

        logger.info("Storage enabled (${configManager.storageType()}) - offline-inclusive leaderboards are available.")
    }

    private fun buildDatabase(): Database = when (configManager.storageType().lowercase()) {
        "mysql", "mariadb" -> {
            val useSsl = configManager.mysqlUseSsl()
            val url = "jdbc:mysql://${configManager.mysqlHost()}:${configManager.mysqlPort()}/" +
                "${configManager.mysqlDatabase()}?useSSL=$useSsl&autoReconnect=true"
            Database(DatabaseType.MYSQL, url, configManager.mysqlUsername(), configManager.mysqlPassword(), configManager.storagePoolSize())
        }
        else -> {
            configManager.sqliteFile().parentFile?.mkdirs()
            Database(DatabaseType.SQLITE, "jdbc:sqlite:${configManager.sqliteFile().absolutePath}")
        }
    }
}
