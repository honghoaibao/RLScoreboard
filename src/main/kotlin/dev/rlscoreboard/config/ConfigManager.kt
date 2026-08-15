package dev.rlscoreboard.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** Loads config.yml/messages.yml and makes sure the scoreboards/ and leaderboards/ folders exist with sane defaults. */
class ConfigManager(private val plugin: JavaPlugin) {

    lateinit var mainConfig: YamlConfiguration
        private set
    lateinit var messages: YamlConfiguration
        private set

    val scoreboardsFolder: File get() = File(plugin.dataFolder, "scoreboards")
    val leaderboardsFolder: File get() = File(plugin.dataFolder, "leaderboards")

    fun loadAll() {
        plugin.saveDefaultConfig()
        mainConfig = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config.yml"))

        saveResourceIfMissing("messages.yml")
        messages = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "messages.yml"))

        ensureDefaultDirectory(scoreboardsFolder, listOf("scoreboards/survival.yml", "scoreboards/lobby.yml"))
        ensureDefaultDirectory(leaderboardsFolder, listOf("leaderboards/topkills.yml", "leaderboards/richest.yml"))
    }

    fun reload() {
        plugin.reloadConfig()
        mainConfig = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config.yml"))
        messages = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "messages.yml"))
    }

    fun message(path: String, default: String): String = messages.getString(path, default) ?: default

    /** Active language code from config.yml's `language` key (section 4 of the localization spec). Defaults to "en". */
    fun language(): String = mainConfig.getString("language", "en") ?: "en"

    fun heartbeatTicks(): Long = mainConfig.getLong("engine.heartbeat-ticks", 4L).coerceAtLeast(1L)

    // ---- Storage (Phase 2) ----
    fun storageEnabled(): Boolean = mainConfig.getBoolean("storage.enabled", false)
    fun storageType(): String = mainConfig.getString("storage.type", "sqlite") ?: "sqlite"
    fun sqliteFile(): File = File(plugin.dataFolder, mainConfig.getString("storage.sqlite.file", "data.db") ?: "data.db")
    fun mysqlHost(): String = mainConfig.getString("storage.mysql.host", "localhost") ?: "localhost"
    fun mysqlPort(): Int = mainConfig.getInt("storage.mysql.port", 3306)
    fun mysqlDatabase(): String = mainConfig.getString("storage.mysql.database", "rlscoreboard") ?: "rlscoreboard"
    fun mysqlUsername(): String = mainConfig.getString("storage.mysql.username", "root") ?: "root"
    fun mysqlPassword(): String = mainConfig.getString("storage.mysql.password", "") ?: ""
    fun mysqlUseSsl(): Boolean = mainConfig.getBoolean("storage.mysql.use-ssl", false)
    fun storageSyncTicks(): Long = (mainConfig.getLong("storage.sync-interval-seconds", 60L) * 20L).coerceAtLeast(20L)
    fun storageRefreshTicks(): Long = (mainConfig.getLong("storage.refresh-interval-seconds", 300L) * 20L).coerceAtLeast(20L)
    fun storageHistoryTicks(): Long = (mainConfig.getLong("storage.history-interval-seconds", 3600L) * 20L).coerceAtLeast(20L)
    fun storagePoolSize(): Int = mainConfig.getInt("storage.pool-size", 4).coerceIn(1, 16)

    private fun saveResourceIfMissing(path: String) {
        if (!File(plugin.dataFolder, path).exists()) plugin.saveResource(path, false)
    }

    private fun ensureDefaultDirectory(folder: File, jarResources: List<String>) {
        if (folder.exists()) return
        for (resource in jarResources) {
            runCatching { plugin.saveResource(resource, false) }
        }
    }
}
