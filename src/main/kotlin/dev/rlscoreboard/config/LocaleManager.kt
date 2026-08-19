package dev.rlscoreboard.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.Reader
import java.nio.charset.StandardCharsets

/**
 * Multi-language message system for every player/admin-facing string RLScoreboard sends
 * (English, Vietnamese, Japanese bundled; community locales load automatically - see below).
 * Locale files live in `locales/<code>.yml` under the plugin's data folder - bundled ones are
 * extracted from the jar on first run via the same `saveResource`-if-missing pattern
 * [ConfigManager] already uses for `messages.yml`.
 *
 * Resolution order for [get]/[getMessage]: active language (from `config.yml`'s `language`
 * key) -> "en" -> a safe non-null placeholder. An unrecognised `language` value falls back to
 * "en" with a one-time warning logged (never a crash, never a null/blank message).
 *
 * **No hard-coded language enum** (design spec section M): [availableLocales] is whatever's
 * actually found in `locales/` on disk at load time, not a fixed list in code. Dropping a
 * community-contributed `locales/ko.yml` (or any other code) into that folder and setting
 * `language: ko` in `config.yml` works with zero plugin-code changes - only [BUNDLED_LOCALES]
 * (the set shipped *inside the jar*, extracted automatically) is a fixed list, since that's
 * genuinely a packaging concern, not a language-support limit. See docs/TRANSLATIONS.md.
 */
class LocaleManager(private val plugin: JavaPlugin, private val configManager: ConfigManager) {

    companion object {
        const val DEFAULT_LOCALE = "en"

        /** Locale files bundled inside the jar (one `resources/locales/<code>.yml` per bundled language) and extracted to the data folder on first run. */
        private val BUNDLED_LOCALES = listOf("en", "vi", "ja")

        /** Old flat `messages.yml` key -> new locale key, for the one-time migration in [migrateLegacyMessages]. */
        private val LEGACY_KEY_MIGRATION = mapOf(
            "no-permission" to "no_permission",
            "reloaded" to "reload_success"
        )
    }

    private val localeCache = HashMap<String, YamlConfiguration>()
    private val loggedMissingKeys = HashSet<String>()

    var activeLocale: String = DEFAULT_LOCALE
        private set

    /** Loads (or reloads) every discovered locale file and resolves the active one from config.yml. */
    fun load() {
        val englishExistedBefore = localeFile(DEFAULT_LOCALE).exists()
        BUNDLED_LOCALES.forEach { saveResourceIfMissing("locales/$it.yml") }
        if (!englishExistedBefore) migrateLegacyMessages()

        localeCache.clear()
        loggedMissingKeys.clear()
        discoverLocaleFiles().forEach { code -> localeCache[code] = loadLocaleFile(code) }
        // English always has a cache entry, even if resources/locales/en.yml somehow failed
        // to extract - get()/getMessage() fall back to it unconditionally, so an empty
        // YamlConfiguration here is a safe placeholder rather than a null lookup downstream.
        localeCache.putIfAbsent(DEFAULT_LOCALE, YamlConfiguration())

        val requested = configManager.language().trim().lowercase()
        activeLocale = if (requested in localeCache) {
            requested
        } else {
            plugin.logger.warning("Unknown language '$requested' in config.yml - falling back to '$DEFAULT_LOCALE'.")
            DEFAULT_LOCALE
        }
    }

    fun reload() = load()

    /** Resolves [key] in the active locale. See [getMessage] for the full fallback/placeholder/debug-logging behavior. */
    fun get(key: String, vararg placeholders: Pair<String, String>): String = getMessage(activeLocale, key, *placeholders)

    /** Public-API alias for [get] using the active locale (design spec section J). */
    fun getMessage(key: String, vararg placeholders: Pair<String, String>): String = getMessage(activeLocale, key, *placeholders)

    /**
     * Resolves [key] in [locale] specifically (not necessarily the active one), falling back
     * to English, then to a visible-but-safe placeholder if missing everywhere - never null,
     * never blank. Supports `{token}` substitution via [placeholders], e.g.
     * `getMessage("vi", "leaderboard_created", "id" to id)` for a value containing `{id}`.
     *
     * When `debug: true` in config.yml, the first time a given locale/key combination falls
     * back or is missing entirely gets logged once (design spec section I), so a translator
     * fixing a locale file doesn't have to guess which keys are absent.
     */
    fun getMessage(locale: String, key: String, vararg placeholders: Pair<String, String>): String {
        val direct = localeCache[locale]?.getString(key)
        val raw = direct ?: run {
            logMissingKeyOnce(locale, key)
            localeCache[DEFAULT_LOCALE]?.getString(key)
        } ?: run {
            logMissingKeyOnce(DEFAULT_LOCALE, key)
            "&c[missing locale key: $key]"
        }

        var result = raw
        for ((token, value) in placeholders) {
            result = result.replace("{$token}", value)
        }
        return result
    }

    /** True if [locale] is loaded and defines [key] itself (English fallback doesn't count) - design spec section J. */
    fun hasKey(locale: String, key: String): Boolean = localeCache[locale.lowercase()]?.contains(key) == true

    /** Is [locale] currently loaded (bundled or community, from disk)? */
    fun isSupported(locale: String): Boolean = locale.trim().lowercase() in localeCache

    /** Every currently loaded locale code, English first, rest alphabetical - design spec sections K/M. */
    fun availableLocales(): List<String> = localeCache.keys.sortedWith(compareBy({ it != DEFAULT_LOCALE }, { it }))

    /** Every key defined in the English (reference/fallback) locale. */
    fun referenceKeys(): Set<String> = localeCache[DEFAULT_LOCALE]?.getKeys(false) ?: emptySet()

    /** Every key [locale] defines directly (no fallback resolution). */
    fun keysOf(locale: String): Set<String> = localeCache[locale.lowercase()]?.getKeys(false) ?: emptySet()

    /** Raw, unresolved value for [key] in [locale] only - no fallback, no placeholder substitution. Used by [LocaleValidator], which needs to compare exact source text. Null if [locale] doesn't define [key]. */
    fun rawValue(locale: String, key: String): String? = localeCache[locale.lowercase()]?.getString(key)

    /**
     * Public API (design spec section J/M) for another plugin to register a locale
     * RLScoreboard doesn't ship. [source] must be valid YAML in the same flat key/value shape
     * as `locales/en.yml`. Merges into (doesn't replace) an already-loaded locale of the same
     * code, so a plugin can add a handful of its own keys to an existing language without
     * clobbering the rest of it - this is how a third-party addon plugin could ship its own
     * translated strings for keys *it* defines, entirely without touching RLScoreboard's core.
     */
    fun registerLocale(code: String, source: Reader) {
        val normalized = code.trim().lowercase()
        val incoming = runCatching { YamlConfiguration.loadConfiguration(source) }.getOrElse {
            plugin.logger.warning("Failed to register locale '$normalized' (${it.message}).")
            return
        }
        val target = localeCache.getOrPut(normalized) { YamlConfiguration() }
        incoming.getKeys(false).forEach { key -> target.set(key, incoming.get(key)) }
    }

    private fun logMissingKeyOnce(locale: String, key: String) {
        if (!configManager.debugEnabled()) return
        if (loggedMissingKeys.add("$locale:$key")) {
            plugin.logger.info("[locale debug] '$locale' is missing key '$key'.")
        }
    }

    private fun localeFile(code: String): File = File(plugin.dataFolder, "locales/$code.yml")

    /** Every YAML file actually present on disk under `locales/`: bundled files extracted by [load], plus any community translation an admin dropped in manually - this is what makes [availableLocales] grow with zero code changes (section M). */
    private fun discoverLocaleFiles(): List<String> {
        val folder = File(plugin.dataFolder, "locales")
        val onDisk = folder.listFiles { f -> f.isFile && f.extension.equals("yml", ignoreCase = true) }
            ?.map { it.nameWithoutExtension.lowercase() }
            ?: emptyList()
        return (BUNDLED_LOCALES + onDisk).distinct()
    }

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
     * One-time upgrade path: if this server already had a flat `messages.yml` with a
     * customised `no-permission`/`reloaded` value *before* this locale system existed, carry
     * those values into the freshly-extracted `locales/en.yml` instead of silently discarding
     * an admin's customisation. Only runs the first time `locales/en.yml` doesn't exist yet on
     * disk - never overwrites it again afterwards, so any later direct edits to
     * `locales/en.yml` are left alone on every subsequent load/reload.
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
