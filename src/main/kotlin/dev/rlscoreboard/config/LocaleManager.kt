package dev.rlscoreboard.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Multi-language message system for every player/admin-facing string RLScoreboard sends
 * (English default, Vietnamese added). Locale files live in `locales/<code>.yml` under the
 * plugin's data folder - bundled in the jar as `resources/locales/en.yml` and
 * `resources/locales/vi.yml`, copied out on first run via the same
 * `saveResource`-if-missing pattern [ConfigManager] already uses for `messages.yml`.
 *
 * Resolution order for [get]: active language (from `config.yml`'s `language` key) -> "en"
 * -> a safe non-null placeholder. An unrecognised `language` value falls back to "en" with a
 * one-time warning logged (never a crash, never a null/blank message - section 17/24 of the
 * design spec).
 */
class LocaleManager(private val plugin: JavaPlugin, private val configManager: ConfigManager) {

    companion object {
        const val DEFAULT_LOCALE = "en"
        private val SUPPORTED_LOCALES = listOf("en", "vi")

        /** Old flat `messages.yml` key -> new locale key, for the one-time migration in [migrateLegacyMessages]. */
        private val LEGACY_KEY_MIGRATION = mapOf(
            "no-permission" to "no_permission",
            "reloaded" to "reload_success"
        )
    }

    private val localeCache = HashMap<String, YamlConfiguration>()

    var activeLocale: String = DEFAULT_LOCALE
        private set

    /** Loads (or reloads) every supported locale file and resolves the active one from config.yml. */
    fun load() {
        val englishExistedBefore = localeFile(DEFAULT_LOCALE).exists()
        SUPPORTED_LOCALES.forEach { saveResourceIfMissing("locales/$it.yml") }
        if (!englishExistedBefore) migrateLegacyMessages()

        localeCache.clear()
        SUPPORTED_LOCALES.forEach { code -> localeCache[code] = loadLocaleFile(code) }

        val requested = configManager.language().trim().lowercase()
        activeLocale = if (requested in SUPPORTED_LOCALES) {
            requested
        } else {
            plugin.logger.warning("Unknown language '$requested' in config.yml - falling back to '$DEFAULT_LOCALE'.")
            DEFAULT_LOCALE
        }
    }

    fun reload() = load()

    /**
     * Resolves [key] in the active locale, falling back to English, then to a visible-but-safe
     * placeholder (never null, never blank - section 17). Supports `{token}` substitution via
     * [placeholders], e.g. `get("leaderboard_created", "id" to id)` for a value containing
     * `{id}`.
     */
    fun get(key: String, vararg placeholders: Pair<String, String>): String {
        val raw = localeCache[activeLocale]?.getString(key)
            ?: localeCache[DEFAULT_LOCALE]?.getString(key)
            ?: "&c[missing locale key: $key]"

        var result = raw
        for ((token, value) in placeholders) {
            result = result.replace("{$token}", value)
        }
        return result
    }

    private fun localeFile(code: String): File = File(plugin.dataFolder, "locales/$code.yml")

    private fun loadLocaleFile(code: String): YamlConfiguration {
        val file = localeFile(code)
        if (!file.exists()) return YamlConfiguration()
        return runCatching {
            file.reader(StandardCharsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        }.getOrElse {
            plugin.logger.warning("Failed to load locales/$code.yml (${it.message}) - using bundled defaults for '$code'.")
            YamlConfiguration()
        }
    }

    private fun saveResourceIfMissing(path: String) {
        if (!File(plugin.dataFolder, path).exists()) runCatching { plugin.saveResource(path, false) }
    }

    /**
     * One-time upgrade path (design spec section 17): if this server already had a flat
     * `messages.yml` with a customised `no-permission`/`reloaded` value *before* this locale
     * system existed, carry those values into the freshly-extracted `locales/en.yml` instead
     * of silently discarding an admin's customisation. Only runs the first time
     * `locales/en.yml` doesn't exist yet on disk - never overwrites it again afterwards, so
     * any later direct edits to `locales/en.yml` are left alone on every subsequent load/reload.
     */
    private fun migrateLegacyMessages() {
        val legacy = File(plugin.dataFolder, "messages.yml")
        if (!legacy.exists()) return

        val legacyYaml = runCatching { YamlConfiguration.loadConfiguration(legacy) }.getOrNull() ?: return
        val overrides = LEGACY_KEY_MIGRATION.mapNotNull { (oldKey, newKey) ->
            legacyYaml.getString(oldKey)?.let { newKey to it }
        }
        if (overrides.isEmpty()) return

        val enFile = localeFile(DEFAULT_LOCALE)
        val target = YamlConfiguration.loadConfiguration(enFile)
        overrides.forEach { (key, value) -> target.set(key, value) }
        runCatching { target.save(enFile) }
            .onFailure { plugin.logger.warning("Failed to migrate messages.yml overrides into locales/en.yml (${it.message}).") }
    }
}
