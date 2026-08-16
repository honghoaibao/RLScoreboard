package dev.rlscoreboard.integration

import dev.rlscoreboard.integration.util.VersionRange

/**
 * One optional third-party integration (design spec section A/B). Never depended on
 * directly by core engines - `ScoreboardEngine`, `LeaderboardEngine`, `ConditionEngine`,
 * etc. only ever ask [IntegrationManager] "is capability X available right now", never
 * "is plugin Y installed". That indirection is what lets a whole capability
 * (`%rl_balance%`, a rank placeholder, a datasource) disappear cleanly when its plugin
 * isn't there, instead of scattering `Bukkit.getPluginManager().getPlugin(...) != null`
 * checks across the codebase.
 *
 * Every integration declares its own metadata (section A) rather than the manager hard-coding
 * a table of plugin facts - that keeps `IntegrationManager` itself generic, and means a new
 * integration is "implement this interface", not "also edit the manager".
 */
interface Integration {

    /** Lookup id used by [IntegrationManager]/commands/docs, e.g. "worldguard". Lowercase, no spaces. */
    val id: String

    /** Human-readable name for logs/diagnostics/docs, e.g. "WorldGuard". */
    val pluginName: String

    /** The other plugin's own `plugin.yml` name, used for `Bukkit.getPluginManager().getPlugin(...)`. Defaults to [pluginName]. */
    val pluginId: String get() = pluginName

    /** Lowest version of the target plugin this integration has been tested against, e.g. "5.0". */
    val minSupportedVersion: String

    /** Highest version of the target plugin this integration has been tested against, e.g. "5.4". */
    val maxTestedVersion: String

    /**
     * True only for an integration RLScoreboard cannot function at all without. As of this
     * writing that's none of them - every integration in this plugin is optional by design
     * (section 3/B: "TUYỆT ĐỐI KHÔNG hard-code... Economy provider cụ thể"), so the default
     * is `false` and no built-in integration currently overrides it.
     */
    val required: Boolean get() = false

    /**
     * What this integration can provide once [status] is [IntegrationStatus.SUPPORTED] -
     * e.g. `setOf("economy")` for Vault, `setOf("rank", "prefix")` for LuckPerms. Declared
     * even for capabilities that are only sometimes live (see [VaultIntegration] for the
     * "Vault present, no economy provider registered" case from section D) - use
     * [IntegrationManager.hasCapability] for the live, moment-of-call answer, not this set.
     */
    val capabilities: Set<String>

    /** Current lifecycle state. Owned and updated by [IntegrationManager]; integrations don't set this themselves. */
    var status: IntegrationStatus

    /** The target plugin's own runtime version string (from its `plugin.yml`), or null if not installed. Owned by [IntegrationManager]. */
    var detectedVersion: String?

    /** Is the target plugin installed at all, regardless of version compatibility? [AbstractIntegration]'s default: a plain Bukkit plugin-manager lookup by [pluginId]. */
    fun isInstalled(): Boolean

    /**
     * Reads the installed plugin's own version string. Only called when [isInstalled] is
     * true. [AbstractIntegration]'s default implementation reads it straight off the target
     * plugin's own `plugin.yml` (`Plugin.getPluginMeta().getVersion()`) - this works for
     * *every* softdepend here without needing a compile-time API dependency just to know a
     * version number, which is why integrations that are detection-only (no compiled API)
     * still get real version-aware compatibility checking for free.
     */
    fun readVersion(): String?

    /**
     * Does [version] fall within [minSupportedVersion]..[maxTestedVersion]? Default
     * implementation ([VersionRange]) compares major version numbers only (matching how
     * every range in this codebase is expressed, e.g. "5.x", "2.x") and treats an
     * unparseable version string as compatible rather than blocking the integration on a
     * false negative - see that class's KDoc for the exact rule. Override for an integration
     * that needs stricter comparison.
     */
    fun isVersionCompatible(version: String): Boolean =
        VersionRange.isCompatible(version, minSupportedVersion, maxTestedVersion)

    /**
     * Wires this integration into RLScoreboard's engines (registers placeholders,
     * datasources, etc.). Only ever called by [IntegrationManager] once, right after
     * [status] is set to [IntegrationStatus.SUPPORTED] - never called for a plugin that's
     * missing or version-incompatible.
     */
    fun enable()

    /**
     * Reverses [enable] - unregisters whatever it registered. Called by [IntegrationManager]
     * before a re-detection pass (e.g. a future `/rlscoreboard integrations reload`), so an
     * integration that registers stateful listeners/tasks should clean them up here. Default
     * no-op, which is correct for every current integration (they only register
     * placeholders/datasources, which are simply overwritten on re-registration).
     */
    fun disable() {}

    /**
     * Is [capability] usable *right now* (design spec section D)? Default: the integration
     * is [IntegrationStatus.SUPPORTED] and declared [capability] in [capabilities]. Override
     * this - not [capabilities] - for a capability that can go stale after [enable] runs
     * without the plugin itself being un-detected, e.g. Vault staying installed while its
     * economy provider unregisters: `VaultIntegration` overrides this to also check
     * `Bukkit.getServicesManager().getRegistration(Economy::class.java)` for the "economy"
     * capability specifically, so `%rl_balance%`/`economy` datasource callers get a live
     * "unavailable" instead of a stale "yes" from startup.
     */
    fun hasCapability(capability: String): Boolean = status == IntegrationStatus.SUPPORTED && capability in capabilities
}
