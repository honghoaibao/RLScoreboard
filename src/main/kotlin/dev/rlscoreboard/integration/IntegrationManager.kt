package dev.rlscoreboard.integration

import org.bukkit.plugin.Plugin

/**
 * Runs the detect -> version-check -> enable-or-disable-gracefully flow (design spec section
 * C) for every registered [Integration], and is the single place code elsewhere in
 * RLScoreboard asks "is capability X available" instead of reaching for
 * `Bukkit.getPluginManager().getPlugin(...)` itself. Never crashes the plugin because an
 * integration is missing, outdated, incompatible, or throws during [Integration.enable] -
 * every one of those degrades to a logged line and that integration's [IntegrationStatus],
 * never an uncaught exception.
 */
class IntegrationManager(private val plugin: Plugin) {

    private var loaded: List<Integration> = emptyList()

    /** All registered integrations, in the order [loadAll] received them - used by `/rlscoreboard integrations` and docs generation. */
    fun integrations(): List<Integration> = loaded

    fun find(id: String): Integration? = loaded.firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** True if [id]'s integration reached [IntegrationStatus.SUPPORTED]. */
    fun isEnabled(id: String): Boolean = find(id)?.status == IntegrationStatus.SUPPORTED

    /** True if [id]'s integration is [IntegrationStatus.SUPPORTED] *and* currently provides [capability] - see [Integration.hasCapability]. */
    fun hasCapability(id: String, capability: String): Boolean = find(id)?.hasCapability(capability) == true

    /** True if *any* supported integration currently provides [capability] - useful when a caller doesn't care which plugin backs it (e.g. "is any economy provider available"). */
    fun hasCapability(capability: String): Boolean = loaded.any { it.hasCapability(capability) }

    fun loadAll(integrations: List<Integration>) {
        loaded = integrations
        plugin.logger.info("Loading integrations...")
        plugin.logger.info("\u2713 Core")

        for (integration in integrations) {
            detectAndEnable(integration)
        }

        plugin.logger.info("Initialization complete.")
    }

    private fun detectAndEnable(integration: Integration) {
        val installed = runCatching { integration.isInstalled() }.getOrDefault(false)
        if (!installed) {
            integration.status = IntegrationStatus.NOT_INSTALLED
            integration.detectedVersion = null
            plugin.logger.info("- ${integration.pluginName} not installed")
            return
        }

        val version = runCatching { integration.readVersion() }.getOrNull()
        integration.detectedVersion = version

        // An unreadable version string doesn't block the integration - see VersionRange's
        // KDoc for why an unparseable/missing version is treated as compatible rather than
        // as a false-negative disable.
        val compatible = version == null || runCatching { integration.isVersionCompatible(version) }.getOrDefault(true)

        if (!compatible) {
            integration.status = IntegrationStatus.UNSUPPORTED_VERSION
            plugin.logger.warning(
                "${integration.pluginName} detected (version $version), but that's outside the tested range " +
                    "(${integration.minSupportedVersion}-${integration.maxTestedVersion}). Integration has been disabled safely."
            )
            return
        }

        runCatching { integration.enable() }
            .onSuccess {
                integration.status = IntegrationStatus.SUPPORTED
                val versionLabel = version?.let { " $it" } ?: ""
                plugin.logger.info("\u2713 ${integration.pluginName}$versionLabel")
            }
            .onFailure {
                integration.status = IntegrationStatus.FAILED
                plugin.logger.warning("Failed to enable ${integration.pluginName} integration: ${it.message}")
            }
    }
}
