package dev.rlscoreboard.integration

/**
 * Lifecycle state of one optional [Integration], reported by [IntegrationManager] and shown
 * in `/rlscoreboard integrations`, `/rlscoreboard status`, and `/rlscoreboard debug` (design
 * spec section 4/25). Six states, not a flat "supported or not" - see each entry for what
 * distinguishes it. The detection flow is always: installed? -> version read? -> where does
 * it fall vs the tested range? -> enable, and record which capabilities actually wired. There
 * is no path that lets a detection/enable failure crash the plugin - every failure mode here
 * degrades to one of these states with a logged reason, never an uncaught exception.
 */
enum class IntegrationStatus(val icon: String) {
    /** Target plugin not found via `Bukkit.getPluginManager().getPlugin(...)`. Default state before detection runs. */
    NOT_INSTALLED("\u25CB"),

    /**
     * Installed, version read successfully, and numerically *below*
     * [Integration.minSupportedVersion] - genuinely risky, since an older release may simply
     * not have the API methods this integration calls. Not enabled.
     */
    INCOMPATIBLE("\u274C"),

    /**
     * Installed, but either the version couldn't be parsed at all, or it's numerically
     * *above* [Integration.maxTestedVersion] (design spec section 4: "không được nói...
     * unsupported chỉ vì version mới hơn maximum"). Enabled optimistically - a newer release
     * is far more likely to still work than an older one - but reported distinctly so an
     * admin knows this specific version combination hasn't been verified.
     */
    DETECTED_UNTESTED("\u26A0"),

    /**
     * Enabled, but [Integration.activeCapabilities] is a strict subset of
     * [Integration.capabilities] - some, not all, of what this integration declares it can
     * do actually wired up successfully (see [Integration.enable]'s KDoc for how one
     * capability failing doesn't take the others down with it).
     */
    PARTIALLY_SUPPORTED("\u26A0"),

    /** Enabled and every declared capability wired successfully. The fully-working state. */
    SUPPORTED("\u2705"),

    /**
     * [Integration.isInstalled]/[Integration.readVersion] threw, or [Integration.enable]
     * threw before wiring anything at all. Distinct from [INCOMPATIBLE] - this is an
     * unexpected failure, not a normal "version too old" outcome - logged with the exception
     * message so it's diagnosable.
     */
    ERROR("\u274C");

    /** [SUPPORTED], [PARTIALLY_SUPPORTED], and [DETECTED_UNTESTED] all mean "enabled, capabilities may be live" - the three non-enabled states never have anything in [Integration.activeCapabilities]. */
    val isEnabled: Boolean get() = this == SUPPORTED || this == PARTIALLY_SUPPORTED || this == DETECTED_UNTESTED
}
