package dev.rlscoreboard.config

import dev.rlscoreboard.api.model.LeaderboardDefinition
import dev.rlscoreboard.api.model.LeaderboardLocation
import dev.rlscoreboard.condition.ConditionParser
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** Loads every *.yml file under leaderboards/, same one-or-many-per-file convention as [BoardConfigLoader]. */
class LeaderboardConfigLoader(private val plugin: JavaPlugin, private val folder: File) {

    fun loadAll(): Map<String, LeaderboardDefinition> {
        val result = LinkedHashMap<String, LeaderboardDefinition>()
        val files = folder.listFiles { f -> f.isFile && f.extension.equals("yml", ignoreCase = true) } ?: emptyArray()

        for (file in files.sortedBy { it.name }) {
            val yaml = runCatching { YamlConfiguration.loadConfiguration(file) }.getOrElse {
                plugin.logger.warning("Failed to parse ${file.name}: ${it.message}")
                continue
            }
            val root = yaml.getConfigurationSection("leaderboards") ?: continue
            for (id in root.getKeys(false)) {
                val section = root.getConfigurationSection(id) ?: continue
                runCatching { parseLeaderboard(id, section) }
                    .onSuccess { result[id] = it }
                    .onFailure { plugin.logger.warning("Invalid leaderboard '$id' in ${file.name}: ${it.message}") }
            }
        }
        return result
    }

    /** Writes a brand-new `<id>.yml` for a leaderboard created via `/rlscoreboard leaderboard create`. */
    fun save(definition: LeaderboardDefinition) {
        val file = File(folder, "${definition.id}.yml")
        val yaml = YamlConfiguration()
        val path = "leaderboards.${definition.id}"
        yaml.set("$path.enabled", definition.enabled)
        yaml.set("$path.display.type", definition.displayType)
        yaml.set("$path.title", definition.title)
        yaml.set("$path.datasource.type", definition.dataSourceId)
        yaml.set("$path.entries", definition.entries)
        yaml.set("$path.update.interval", definition.updateIntervalTicks / 20L)
        yaml.set("$path.format", definition.entryFormat)
        runCatching { yaml.save(file) }
            .onFailure { plugin.logger.warning("Failed to save leaderboards/${definition.id}.yml: ${it.message}") }
    }

    fun saveLocation(id: String, location: LeaderboardLocation) {
        val file = fileFor(id) ?: File(folder, "$id.yml")
        val yaml = YamlConfiguration.loadConfiguration(file)
        val path = "leaderboards.$id.location"
        yaml.set("$path.world", location.world)
        yaml.set("$path.x", location.x)
        yaml.set("$path.y", location.y)
        yaml.set("$path.z", location.z)
        yaml.set("$path.yaw", location.yaw.toDouble())
        yaml.set("$path.pitch", location.pitch.toDouble())
        runCatching { yaml.save(file) }
            .onFailure { plugin.logger.warning("Failed to save location for '$id': ${it.message}") }
    }

    fun deleteFile(id: String): Boolean {
        val file = fileFor(id) ?: File(folder, "$id.yml")
        return if (file.exists()) file.delete() else false
    }

    private fun fileFor(id: String): File? =
        folder.listFiles { f -> f.isFile && f.extension.equals("yml", ignoreCase = true) }
            ?.firstOrNull { runCatching { YamlConfiguration.loadConfiguration(it).isConfigurationSection("leaderboards.$id") }.getOrDefault(false) }

    private fun parseLeaderboard(id: String, section: ConfigurationSection): LeaderboardDefinition {
        val displayType = section.getConfigurationSection("display")?.getString("type", "SIDEBAR") ?: "SIDEBAR"

        val title = section.getStringList("title")
            .ifEmpty { listOfNotNull(section.getString("title")) }
            .ifEmpty { listOf(id) }

        val dataSourceSection = section.getConfigurationSection("datasource")
        val dataSourceId = dataSourceSection?.getString("type", "manual") ?: "manual"
        val dataSourceArgs = dataSourceSection?.getKeys(false)
            ?.filterNot { it == "type" }
            ?.associateWith { key -> dataSourceSection.getString(key, "") ?: "" }
            ?: emptyMap()

        val intervalSeconds = section.getConfigurationSection("update")?.getLong("interval") ?: 60L
        val format = section.getStringList("format").ifEmpty { listOf("&e%position% &f%player% &7%value%") }

        val topIcons = section.getConfigurationSection("top")?.getKeys(false)
            ?.mapNotNull { key -> key.toIntOrNull()?.let { it to (section.getString("top.$key") ?: "") } }
            ?.toMap() ?: emptyMap()

        val location = section.getConfigurationSection("location")?.let { loc ->
            val world = loc.getString("world") ?: return@let null
            LeaderboardLocation(
                world = world,
                x = loc.getDouble("x"), y = loc.getDouble("y"), z = loc.getDouble("z"),
                yaw = loc.getDouble("yaw", 0.0).toFloat(), pitch = loc.getDouble("pitch", 0.0).toFloat()
            )
        }

        return LeaderboardDefinition(
            id = id,
            enabled = section.getBoolean("enabled", true),
            displayType = displayType.uppercase(),
            title = title,
            dataSourceId = dataSourceId,
            dataSourceArgs = dataSourceArgs,
            entries = section.getInt("entries", 10).coerceIn(1, 15),
            updateIntervalTicks = (intervalSeconds * 20L).coerceAtLeast(20L),
            entryFormat = format,
            topIcons = topIcons,
            location = location,
            priority = section.getInt("priority", 0),
            conditions = ConditionParser.parse(section.getConfigurationSection("conditions"))
        )
    }
}
