package dev.rlscoreboard.api

/**
 * Public, stable entry point for other plugins integrating with RLScoreboard.
 * Get the active instance with [RLScoreboardAPI.get] once RLScoreboard has enabled
 * (e.g. from your own plugin's `onEnable`, after checking `getServer().getPluginManager()
 * .getPlugin("RLScoreboard") != null`).
 *
 * API version: 1 ([API_VERSION]). Any breaking change bumps this constant and is called
 * out in CHANGELOG.md before release - see section 21 of the design spec.
 */
interface RLScoreboardAPI {

    fun registerPlaceholder(identifier: String, provider: PlaceholderProvider)
    fun unregisterPlaceholder(identifier: String)

    fun registerConditionProvider(id: String, provider: ConditionProvider)

    fun registerDataSource(dataSource: LeaderboardDataSource)
    fun unregisterDataSource(id: String)

    fun registerLeaderboardRenderer(renderer: LeaderboardRenderer)

    fun board(): BoardAPI
    fun leaderboard(): LeaderboardAPI

    companion object {
        const val API_VERSION = 1

        @Volatile
        private var instance: RLScoreboardAPI? = null

        @JvmStatic
        fun get(): RLScoreboardAPI = instance
            ?: error("RLScoreboard API is not ready yet - is RLScoreboard installed and enabled?")

        @JvmSynthetic
        internal fun register(api: RLScoreboardAPI) {
            instance = api
        }

        @JvmSynthetic
        internal fun unregister() {
            instance = null
        }
    }
}
