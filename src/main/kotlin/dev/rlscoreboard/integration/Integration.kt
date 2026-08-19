package dev.rlscoreboard.integration

import dev.rlscoreboard.integration.util.VersionRange

/**
 * One optional third-party integration (design spec section 5). Never depended on directly
 * by core engines - `ScoreboardEngine`, `LeaderboardEngine`, `ConditionEngine`, etc. only ever
 * ask [IntegrationManager] "is capability X available", never "is plugin Y installed". That
 * indirection is what lets a whole capability (`%rl_balance%`, a rank placeholder, a
 * datasource) disappear cleanly when its plugin isn't there, instead of scattering
 * `Bukkit.getPluginManager().getPlugin(...) != null` checks across the codebase.
 *
 * Every integration declares its own metadata (section 5/6) rather than the manager
 * hard-coding a table of plugin facts - a new integration is "implement this interface", not
 * "also edit the manager".
 */
interface Integration {

    /** Lookup id used by [IntegrationManager]/commands/docs, e.g. "worldguard". Lowercase, no spaces. */
    val id: String

    /** Human-readable name for logs/diagnostics/docs, e.g. "WorldGuard". */
    val pluginName: String

    /** The other plugin's own `plugin.yml` name, used for `Bukkit.getPluginManager().getPlugin(...)`. Defaults to [pluginName]. */
    val pluginId: String get() = pluginName

    /** Lowest version of the target plugin this integration has been tested against, e.g. "5.0". See [VersionRange] for exactly how this bound is compared. */
    val minSupportedVersion: String

    /** Highest version of the target plugin this integration has been tested against, e.g. "5.4". See [VersionRange] for exactly how this bound is compared. */
    val maxTestedVersion: String

    /**
     * True only for an integration RLScoreboard cannot function at all without. As of this
     * writing that's none of them - every integration in this plugin is optional by design
     * (section 3: "TUYỆT ĐỐI KHÔNG hard-code... Economy provider cụ thể"), so the default is
     * `false` and no built-in integration currently overrides it.
     */
    val required: Boolean get() = false

    /**
     * Everything this integration is *capable* of once fully enabled, e.g. `setOf("rank",
     * "prefix")` for LuckPerms - the full wishlist, declared up front regardless of whether
     * every one of them actually wires up successfully on a given server. Compare against
     * [activeCapabilities] (what's *actually* live right now) to see the difference; that gap
     * is exactly what makes a status [IntegrationStatus.PARTIALLY_SUPPORTED] instead of
     * [IntegrationStatus.SUPPORTED].
     */
    val capabilities: Set<String>

    /** Current lifecycle state. Owned and updated by [IntegrationManager]; integrations don't set this themselves. */
    var status: IntegrationStatus

    /** The target plugin's own runtime version string (from its `plugin.yml`), or null if not installed. Owned by [IntegrationManager]. */
    var detectedVersion: String?

    /**
     * The subset of [capabilities] that actually wired up successfully the last time
     * [enable] ran - see [enable]'s KDoc. Owned by [IntegrationManager], which sets this
     * straight from [enable]'s return value; empty whenever [status] isn't one of
     * [IntegrationStatus.isEnabled].
     */
    var activeCapabilities: Set<String>

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
     * Where [version] falls against [minSupportedVersion]/[maxTestedVersion]. Default:
     * [VersionRange.evaluate]. Override only if an integration needs a genuinely different
     * comparison rule than every other one here.
     */
    fun versionRangeResult(version: String): VersionRange.RangeResult =
        VersionRange.evaluate(version, minSupportedVersion, maxTestedVersion)

    /**
     * Wires this integration into RLScoreboard's engines (registers placeholders,
     * datasources, etc.) and returns exactly which of [capabilities] actually succeeded.
     * Only ever called by [IntegrationManager] once, right after detection decides this
     * integration should be enabled (installed + [IntegrationStatus.INCOMPATIBLE] ruled out).
     *
     * **Isolate each capability's own registration** (e.g. with its own `runCatching`) rather
     * than wrapping the whole method in one try/catch - if this integration declares two
     * independent capabilities and only one throws (a single unexpected API method missing
     * on this particular version, say), the other capability should still come up. Returning
     * a strict subset of [capabilities] is exactly what makes [IntegrationStatus.PARTIALLY_SUPPORTED]
     * meaningful instead of an all-or-nothing [IntegrationStatus.SUPPORTED]/[IntegrationStatus.ERROR]
     * choice - see [LuckPermsIntegration][dev.rlscoreboard.integration.luckperms.LuckPermsIntegration]
     * for a real example (rank and prefix registered independently). An integration that
     * throws all the way out of [enable] (nothing caught) is [IntegrationStatus.ERROR], not
     * partial - [IntegrationManager] treats an uncaught exception as zero capabilities wired.
     */
    fun enable(): Set<String>

    /**
     * Reverses [enable] - unregisters whatever it registered. Called by [IntegrationManager]
     * before a re-detection pass (e.g. a future `/rlscoreboard integrations reload`), so an
     * integration that registers stateful listeners/tasks should clean them up here. Default
     * no-op, which is correct for every current integration (they only register
     * placeholders/datasources, which are simply overwritten on re-registration).
     */
    fun disable() {}

    /**
     * Is [capability] usable *right now* (design spec section 4/5)? Default: [status] is one
     * of [IntegrationStatus.isEnabled] and [capability] is in [activeCapabilities] - the
     * *live* set, not the declared wishlist in [capabilities]. Override this for a capability
     * that can go stale after [enable] runs without the plugin itself being un-detected, e.g.
     * Vault staying installed while its economy provider unregisters:
     * [VaultIntegration][dev.rlscoreboard.integration.vault.VaultIntegration] overrides this
     * to also check `Bukkit.getServicesManager().getRegistration(Economy::class.java)` for
     * the "economy" capability specifically, so `%rl_balance%`/`economy` datasource callers
     * get a live "unavailable" instead of a stale "yes" from startup.
     */
    fun hasCapability(capability: String): Boolean = status.isEnabled && capability in activeCapabilities
}
