package dev.rlscoreboard.integration

import dev.rlscoreboard.integration.util.VersionRange
import org.bukkit.plugin.Plugin

/**
 * Runs the detect -> range-check -> enable flow (design spec section 4/5) for every
 * registered [Integration], and is the single place code elsewhere in RLScoreboard asks "is
 * capability X available" instead of reaching for `Bukkit.getPluginManager().getPlugin(...)`
 * itself. Never crashes the plugin because an integration is missing, outdated, incompatible,
 * or throws during detection/[Integration.enable] - every one of those degrades to a logged
 * line and one of the six [IntegrationStatus] values, never an uncaught exception.
 */
class IntegrationManager(private val plugin: Plugin) {

    private var loaded: List<Integration> = emptyList()

    /** All registered integrations, in the order [loadAll] received them - used by `/rlscoreboard integrations`/`status` and docs generation. */
    fun integrations(): List<Integration> = loaded

    fun find(id: String): Integration? = loaded.firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** True if [id]'s integration reached an "enabled" status - see [IntegrationStatus.isEnabled]. */
    fun isEnabled(id: String): Boolean = find(id)?.status?.isEnabled == true

    /** True if [id]'s integration is enabled *and* currently provides [capability] - see [Integration.hasCapability]. */
    fun hasCapability(id: String, capability: String): Boolean = find(id)?.hasCapability(capability) == true

    /** True if *any* enabled integration currently provides [capability] - useful when a caller doesn't care which plugin backs it (e.g. "is any economy provider available"). */
    fun hasCapability(capability: String): Boolean = loaded.any { it.hasCapability(capability) }

    fun loadAll(integrations: List<Integration>) {
        loaded = integrations
        plugin.logger.info("Loading integrations...")
        plugin.logger.info("\u2705 Core")

        for (integration in integrations) {
            detectAndEnable(integration)
        }

        plugin.logger.info("Initialization complete.")
    }

    private fun detectAndEnable(integration: Integration) {
        val installed = runCatching { integration.isInstalled() }
            .onFailure { logError(integration, "checking whether it's installed", it) }
            .getOrDefault(false)

        if (!installed) {
            integration.status = IntegrationStatus.NOT_INSTALLED
            integration.detectedVersion = null
            integration.activeCapabilities = emptySet()
            plugin.logger.info("${IntegrationStatus.NOT_INSTALLED.icon} ${integration.pluginName} - not installed")
            return
        }

        val version = runCatching { integration.readVersion() }
            .onFailure { logError(integration, "reading its version", it) }
            .getOrNull()
        integration.detectedVersion = version

        // No readable version at all (readVersion() returned null, not "unparseable text") is
        // treated the same as UNPARSEABLE below - can't disprove compatibility, so enable
        // optimistically and report it as untested rather than silently assuming SUPPORTED.
        val rangeResult = version?.let { runCatching { integration.versionRangeResult(it) }.getOrNull() }
            ?: VersionRange.RangeResult.UNPARSEABLE

        if (rangeResult == VersionRange.RangeResult.BELOW_MINIMUM) {
            integration.status = IntegrationStatus.INCOMPATIBLE
            integration.activeCapabilities = emptySet()
            plugin.logger.warning(
                "${integration.pluginName} detected (version $version), but that's below the minimum tested " +
                    "version (${integration.minSupportedVersion}). Integration has been disabled safely."
            )
            return
        }

        val enableResult = runCatching { integration.enable() }
        enableResult.onFailure {
            integration.status = IntegrationStatus.ERROR
            integration.activeCapabilities = emptySet()
            logError(integration, "enabling", it)
            return
        }

        val active = enableResult.getOrDefault(emptySet()).intersect(integration.capabilities)
        integration.activeCapabilities = active

        integration.status = when {
            rangeResult != VersionRange.RangeResult.WITHIN_RANGE -> IntegrationStatus.DETECTED_UNTESTED
            active.size < integration.capabilities.size -> IntegrationStatus.PARTIALLY_SUPPORTED
            else -> IntegrationStatus.SUPPORTED
        }

        val versionLabel = version?.let { " $it" } ?: ""
        val note = when (rangeResult) {
            VersionRange.RangeResult.ABOVE_MAXIMUM -> " (newer than tested max ${integration.maxTestedVersion} - not yet verified)"
            VersionRange.RangeResult.UNPARSEABLE -> " (unrecognised version format - not yet verified)"
            else -> if (integration.status == IntegrationStatus.PARTIALLY_SUPPORTED) {
                val missing = integration.capabilities - active
                " (partial - missing: ${missing.joinToString(", ")})"
            } else ""
        }
        plugin.logger.info("${integration.status.icon} ${integration.pluginName}$versionLabel$note")
    }

    private fun logError(integration: Integration, doing: String, error: Throwable) {
        plugin.logger.warning("${integration.pluginName} integration error while $doing: ${error.message}")
    }
}
