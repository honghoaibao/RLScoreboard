package dev.rlscoreboard.config

import dev.rlscoreboard.api.model.AnimatedText
import dev.rlscoreboard.api.model.BoardDefinition
import dev.rlscoreboard.api.model.BoardLine
import dev.rlscoreboard.condition.ConditionParser
import dev.rlscoreboard.condition.ConditionSet
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Loads every *.yml file under scoreboards/. Each file's root is a `scoreboards:` map so a
 * single file can hold one board (the recommended layout, section 15) or several at once -
 * both are supported without RLScoreboard caring which convention an owner picked.
 */
class BoardConfigLoader(private val plugin: JavaPlugin, private val folder: File) {

    fun loadAll(): Map<String, BoardDefinition> {
        val result = LinkedHashMap<String, BoardDefinition>()
        val files = folder.listFiles { f -> f.isFile && f.extension.equals("yml", ignoreCase = true) } ?: emptyArray()

        for (file in files.sortedBy { it.name }) {
            val yaml = runCatching { YamlConfiguration.loadConfiguration(file) }.getOrElse {
                plugin.logger.warning("Failed to parse ${file.name}: ${it.message}")
                continue
            }
            val root = yaml.getConfigurationSection("scoreboards") ?: continue
            for (id in root.getKeys(false)) {
                val section = root.getConfigurationSection(id) ?: continue
                runCatching { parseBoard(id, section) }
                    .onSuccess { result[id] = it }
                    .onFailure { plugin.logger.warning("Invalid scoreboard '$id' in ${file.name}: ${it.message}") }
            }
        }
        return result
    }

    private fun parseBoard(id: String, section: ConfigurationSection): BoardDefinition {
        val title = parseAnimatedText(section.getConfigurationSection("title"), section.getString("title"))
        val lines = parseLines(section)
        val intervalSeconds = section.getConfigurationSection("update")?.getLong("interval") ?: 10L

        return BoardDefinition(
            id = id,
            enabled = section.getBoolean("enabled", true),
            priority = section.getInt("priority", 0),
            title = title,
            lines = lines,
            updateIntervalTicks = (intervalSeconds * 20L).coerceAtLeast(10L),
            conditions = ConditionParser.parse(section.getConfigurationSection("conditions")),
            fallback = section.getBoolean("fallback", false)
        )
    }

    /**
     * `lines:` accepts either plain strings (the common case, section 6's example) or maps
     * with `text` / `condition(s)` / `animation` keys for a per-line condition or animation:
     * ```
     * lines:
     *   - "&7Always shown"
     *   - text: "&aOnly in creative"
     *     conditions: { gamemode: [creative] }
     *   - text: "&ePulse"
     *     animation: { enabled: true, interval: 5, frames: ["&ePulse", "&6Pulse", "&fPulse"] }
     * ```
     */
    private fun parseLines(section: ConfigurationSection): List<BoardLine> {
        val raw = section.getList("lines") ?: return emptyList()
        return raw.mapNotNull { entry ->
            when (entry) {
                is String -> BoardLine(AnimatedText.static(entry), ConditionSet.EMPTY)
                is Map<*, *> -> parseLineMap(entry)
                else -> null
            }
        }
    }

    private fun parseLineMap(raw: Map<*, *>): BoardLine? {
        val text = raw["text"]?.toString() ?: return null
        val animatedText = parseLineAnimation(raw["animation"], text)

        val conditionsRaw = (raw["conditions"] ?: raw["condition"]) as? Map<*, *>
        val conditions = if (conditionsRaw != null) {
            val section = MemoryConfiguration().createSection("conditions", conditionsRaw)
            ConditionParser.parse(section)
        } else {
            ConditionSet.EMPTY
        }

        return BoardLine(animatedText, conditions)
    }

    private fun parseLineAnimation(raw: Any?, plainFallback: String): AnimatedText {
        val map = raw as? Map<*, *> ?: return AnimatedText.static(plainFallback)
        if (map["enabled"] != true) return AnimatedText.static(plainFallback)

        val frames = (map["frames"] as? List<*>)?.map { it.toString() } ?: emptyList()
        if (frames.isEmpty()) return AnimatedText.static(plainFallback)

        val interval = ((map["interval"] as? Number)?.toLong() ?: 5L) * 20L
        val loop = map["loop"] as? Boolean ?: true
        return AnimatedText(frames, interval, loop)
    }

    private fun parseAnimatedText(section: ConfigurationSection?, plainFallback: String?): AnimatedText {
        if (section == null) return AnimatedText.static(plainFallback ?: "")
        val text = section.getString("text") ?: plainFallback ?: ""
        val animation = section.getConfigurationSection("animation")
        if (animation != null && animation.getBoolean("enabled", false)) {
            val frames = animation.getStringList("frames")
            if (frames.isNotEmpty()) {
                return AnimatedText(
                    frames = frames,
                    intervalTicks = animation.getLong("interval", 5L) * 20L,
                    loop = animation.getBoolean("loop", true)
                )
            }
        }
        return AnimatedText.static(text)
    }
}
