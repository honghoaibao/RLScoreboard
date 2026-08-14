package dev.rlscoreboard.leaderboard

import dev.rlscoreboard.api.LeaderboardRenderer
import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardLocation
import dev.rlscoreboard.condition.ConditionSet
import dev.rlscoreboard.config.LeaderboardConfigLoader
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

/** CRUD + refresh scheduling for leaderboards. Commands (section 14) map almost 1:1 onto this class. */
class LeaderboardManager(
    private val plugin: Plugin,
    private val loader: LeaderboardConfigLoader,
    private val dataSources: DataSourceManager,
    private val ranking: RankingEngine
) {
    private val leaderboards = ConcurrentHashMap<String, LeaderboardDefinition>()
    private val renderers = ConcurrentHashMap<String, LeaderboardRenderer>()
    private val lastRefresh = ConcurrentHashMap<String, Long>()

    fun registerRenderer(renderer: LeaderboardRenderer) {
        renderers[renderer.type.uppercase()] = renderer
    }

    /** Used by commands/renderers that need direct access to a specific renderer, e.g. GUI's on-demand `open()`. */
    fun rendererFor(type: String): LeaderboardRenderer? = renderers[type.uppercase()]

    fun reload() {
        val previous = leaderboards.values.toList()
        leaderboards.clear()
        leaderboards.putAll(loader.loadAll())

        // Tear down displays for any leaderboard that no longer exists after the reload.
        val currentIds = leaderboards.keys
        previous.filterNot { currentIds.contains(it.id) }.forEach { def -> renderers[def.displayType]?.remove(def) }

        plugin.logger.info("Loaded ${leaderboards.size} leaderboard(s).")
    }

    fun all(): Collection<LeaderboardDefinition> = leaderboards.values
    fun get(id: String): LeaderboardDefinition? = leaderboards[id]

    fun create(id: String, displayType: String, dataSourceId: String): LeaderboardDefinition {
        val definition = LeaderboardDefinition(
            id = id,
            enabled = true,
            displayType = displayType.uppercase(),
            title = listOf("&6&l$id"),
            dataSourceId = dataSourceId,
            dataSourceArgs = emptyMap(),
            entries = 10,
            updateIntervalTicks = 20L * 60L,
            entryFormat = listOf("&e%position% &f%player% &7%value%"),
            topIcons = emptyMap(),
            location = null,
            priority = 0,
            conditions = ConditionSet.EMPTY
        )
        leaderboards[id] = definition
        loader.save(definition)
        return definition
    }

    fun delete(id: String): Boolean {
        val def = leaderboards.remove(id) ?: return false
        renderers[def.displayType]?.remove(def)
        loader.deleteFile(id)
        return true
    }

    fun setLocation(id: String, location: LeaderboardLocation) {
        val existing = leaderboards[id] ?: return
        leaderboards[id] = existing.copy(location = location)
        loader.saveLocation(id, location)
    }

    fun forceRefresh(id: String) {
        lastRefresh[id] = 0L
    }

    /** Called on the central heartbeat; refreshes each leaderboard no more often than its own configured interval. */
    fun tick(nowMillis: Long) {
        for (definition in leaderboards.values) {
            if (!definition.enabled) continue

            val intervalMillis = definition.updateIntervalTicks * 50L
            val last = lastRefresh[definition.id] ?: 0L
            if (nowMillis - last < intervalMillis) continue
            lastRefresh[definition.id] = nowMillis

            val source = dataSources.get(definition.dataSourceId) ?: continue
            if (!source.isAvailable()) continue

            val entries = ranking.entriesFor(definition.id, source, definition.entries, intervalMillis)
            renderers[definition.displayType]?.render(definition, entries)
        }
    }
}
