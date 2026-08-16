package dev.rlscoreboard.integration

/**
 * Lifecycle state of one optional [Integration], reported by [IntegrationManager] and shown
 * in `/rlscoreboard integrations` and `/rlscoreboard debug` (design spec section C/F).
 *
 * The detection flow is always: installed? -> version detected? -> compatible? -> [SUPPORTED]
 * or a graceful non-crashing fallback. There is no "crashed" state on purpose - a failure
 * anywhere in that chain degrades to [NOT_INSTALLED]/[UNSUPPORTED_VERSION]/[FAILED], never an
 * uncaught exception that could take the rest of the plugin down with it.
 */
enum class IntegrationStatus {
    /** Target plugin not found via `Bukkit.getPluginManager().getPlugin(...)`. Default state before detection runs. */
    NOT_INSTALLED,

    /** Installed, version read successfully, but outside [Integration.minSupportedVersion]..[Integration.maxTestedVersion]. Disabled safely - never silently pretend compatibility. */
    UNSUPPORTED_VERSION,

    /** Installed and within the tested version range, but [Integration.enable] threw - logged, not crashed. */
    FAILED,

    /** Installed, version compatible (or unverifiable and assumed compatible - see [Integration.isVersionCompatible]), [Integration.enable] ran without throwing. Capabilities are live. */
    SUPPORTED
}
