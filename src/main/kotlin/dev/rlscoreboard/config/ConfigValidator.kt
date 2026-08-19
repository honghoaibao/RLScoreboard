package dev.rlscoreboard.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

/**
 * Config safety/validation helpers (design spec sections 22/28 - config must never be able to
 * crash the plugin or inject anything dangerous; malformed entries are skipped and logged, not
 * evaluated as code) plus [validateInstallation], the startup pass that actually *reports*
 * problems instead of only silently tolerating them.
 *
 * Two specific mistakes this catches that would otherwise fail silently:
 * 1. **A typo'd condition `operator:`** (`"==="` instead of `"=="`, say) doesn't throw -
 *    [dev.rlscoreboard.condition.Operator.parse] intentionally defaults to `EQUALS` for
 *    anything it doesn't recognise, so the board keeps working, just not the way the admin
 *    who wrote it expects, with zero indication anything's wrong. [scanSection] is the only
 *    place that typo becomes visible.
 * 2. **An invalid color code** (`&` not followed by a recognised legacy/hex code) renders as
 *    literal text in-game rather than crashing - also easy to miss without a dedicated scan.
 *    Shares its exact validity rule with [LocaleValidator], which checks the same thing
 *    against locale files specifically.
 */
object ConfigValidator {
    private val ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,64}")
    private val VALID_AMPERSAND_CODE = Regex("""&(#[0-9a-fA-F]{6}|[0-9a-fA-Fk-oK-OrR])""")
    private val RECOGNIZED_OPERATORS = setOf(
        "==", "equals", "!=", "not_equals", "contains", "starts_with", "ends_with",
        ">", "greater_than", "<", "less_than",
        ">=", "greater_or_equal", "greater_than_or_equal",
        "<=", "less_or_equal", "less_than_or_equal"
    )
    private val RECOGNIZED_ANIMATION_PRESETS = setOf(
        "static", "fade", "color", "gradient", "typing", "scrolling", "pulse", "wave"
    )

    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    fun sanitizeId(id: String): String = id.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")

    /** True if every `&` in [text] starts a recognised legacy or `&#RRGGBB` hex color/format code. */
    fun hasValidColorCodes(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            if (text[i] == '&') {
                val match = VALID_AMPERSAND_CODE.matchAt(text, i) ?: return false
                i += match.value.length
            } else {
                i++
            }
        }
        return true
    }

    /** True if [raw] is one of the aliases [dev.rlscoreboard.condition.Operator.parse] actually recognises (case/whitespace-insensitive, matching that method's own normalisation). */
    fun isRecognizedOperator(raw: String): Boolean = raw.trim().lowercase() in RECOGNIZED_OPERATORS

    /** True if [raw] is one of the eight names [dev.rlscoreboard.animation.AnimationPresetFactory.generate] recognises. An unrecognised preset name doesn't throw either - it silently falls back to a single static frame - so, same as [isRecognizedOperator], this is the only way a typo becomes visible instead of just quietly not animating. */
    fun isRecognizedAnimationPreset(raw: String): Boolean = raw.trim().lowercase() in RECOGNIZED_ANIMATION_PRESETS

    /** One problem found by [scanSection]/[validateInstallation]: which file, where in it, and what's wrong - shaped for a one-line log message per issue. */
    data class Issue(val file: String, val path: String, val problem: String)

    /**
     * Recursively walks every string value under [section], flagging invalid color codes
     * anywhere and unrecognised `operator:`/`preset:` values specifically (see class KDoc for
     * why those two get special-cased). Handles both nested [ConfigurationSection]s and YAML
     * lists of maps (the shape `conditions.placeholders: [ {placeholder, operator, value}, ... ]`
     * uses - see [dev.rlscoreboard.condition.ConditionParser]), since Bukkit's config API
     * returns the latter as raw `Map`s, not further [ConfigurationSection]s.
     */
    fun scanSection(fileLabel: String, section: ConfigurationSection, pathPrefix: String = ""): List<Issue> {
        val issues = mutableListOf<Issue>()
        for (key in section.getKeys(false)) {
            val fullPath = if (pathPrefix.isEmpty()) key else "$pathPrefix.$key"
            when (val value = section.get(key)) {
                is ConfigurationSection -> issues += scanSection(fileLabel, value, fullPath)
                is String -> issues += stringIssue(fileLabel, fullPath, key, value)
                is List<*> -> value.forEachIndexed { index, item ->
                    when (item) {
                        is String -> issues += stringIssue(fileLabel, "$fullPath[$index]", key, item)
                        is Map<*, *> -> issues += scanMap(fileLabel, "$fullPath[$index]", item)
                        else -> {}
                    }
                }
                else -> {}
            }
        }
        return issues
    }

    /** Same checks as [scanSection], for a raw `Map` (the shape YAML list items come back as - see class KDoc) instead of a [ConfigurationSection]. Recurses into nested maps - e.g. a line's `animation: {...}` block nested inside a `lines:` list item - so a `preset:`/`operator:` typo is caught no matter how deep it's nested, not just at the top level. */
    private fun scanMap(fileLabel: String, pathPrefix: String, map: Map<*, *>): List<Issue> {
        val issues = mutableListOf<Issue>()
        for ((rawKey, value) in map) {
            val key = rawKey?.toString() ?: continue
            val fullPath = "$pathPrefix.$key"
            when (value) {
                is Map<*, *> -> issues += scanMap(fileLabel, fullPath, value)
                is String -> issues += stringIssue(fileLabel, fullPath, key, value)
                is List<*> -> value.forEachIndexed { index, item ->
                    when (item) {
                        is String -> issues += stringIssue(fileLabel, "$fullPath[$index]", key, item)
                        is Map<*, *> -> issues += scanMap(fileLabel, "$fullPath[$index]", item)
                        else -> {}
                    }
                }
                else -> {}
            }
        }
        return issues
    }

    private fun stringIssue(fileLabel: String, path: String, key: String, value: String): List<Issue> {
        if (key.equals("operator", ignoreCase = true)) {
            return if (isRecognizedOperator(value)) emptyList()
            else listOf(Issue(fileLabel, path, "unrecognised operator '$value' - silently falls back to EQUALS (==)"))
        }
        if (key.equals("preset", ignoreCase = true)) {
            return if (isRecognizedAnimationPreset(value)) emptyList()
            else listOf(Issue(fileLabel, path, "unrecognised animation preset '$value' - silently falls back to a single static frame"))
        }
        return if (hasValidColorCodes(value)) emptyList()
        else listOf(Issue(fileLabel, path, "invalid color code in \"$value\""))
    }

    /**
     * Runs [scanSection] over `config.yml` plus every YAML file under `scoreboards/` and
     * `leaderboards/`, and logs a consolidated report. **Never blocks startup** (section
     * 22: *"Không crash vì config sai... Hiển thị warning rõ"*) - a bad file is reported and
     * skipped for validation purposes, not fatal; whatever actually parses it downstream
     * (`BoardManager`, `LeaderboardManager`) makes its own, separate decision about what to do
     * with a file it can't understand.
     */
    fun validateInstallation(dataFolder: File, logger: Logger) {
        val issues = mutableListOf<Issue>()

        File(dataFolder, "config.yml").takeIf { it.exists() }?.let { file ->
            runCatching { YamlConfiguration.loadConfiguration(file) }
                .onSuccess { issues += scanSection("config.yml", it) }
        }

        for (sub in listOf("scoreboards", "leaderboards")) {
            File(dataFolder, sub).listFiles { f -> f.isFile && f.extension.equals("yml", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    val label = "$sub/${file.name}"
                    runCatching { YamlConfiguration.loadConfiguration(file) }
                        .onSuccess { issues += scanSection(label, it) }
                        .onFailure { issues += Issue(label, "-", "could not parse as YAML (${it.message})") }
                }
        }

        if (issues.isEmpty()) {
            logger.info("Config validation: no issues found.")
            return
        }

        logger.warning("Config validation found ${issues.size} issue(s) - RLScoreboard is still running, but check these:")
        issues.forEach { logger.warning("  ${it.file} :: ${it.path} - ${it.problem}") }
    }
}
