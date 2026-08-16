package dev.rlscoreboard.integration.geyser

import dev.rlscoreboard.integration.AbstractIntegration

/**
 * Detection-only, deliberately - no `geyser-api` compile-time dependency is added for this.
 * Geyser's Bukkit/Paper module registers itself under the plugin name "Geyser-Spigot" (not
 * "Geyser"), which is what [pluginId] looks up.
 *
 * The one capability this integration declares, `"bedrock"`, means exactly "a Bedrock<->Java
 * proxy is running on this server" - that fact alone is genuinely useful (it's what a future
 * `bedrock.safe-mode` renderer switch would gate on, per the wider 0.4.x design spec section
 * 9) and doesn't require calling into Geyser's API at all, just knowing the plugin is there.
 * Anything that needs *which specific player* is connecting via Geyser is
 * [dev.rlscoreboard.integration.floodgate.FloodgateIntegration]'s job, not this one's.
 *
 * Version range is provisional: Geyser has stayed on major version 2.x for a long time, but
 * this hasn't been checked against a live Geyser install in this environment (no network
 * access here to pull a Geyser build) - `/rlscoreboard integrations` will show whatever
 * `detectedVersion` actually comes back as on a real server, which is the real verification
 * this needs before being called "Supported" in docs/INTEGRATIONS.md with any confidence.
 */
class GeyserIntegration : AbstractIntegration() {
    override val id = "geyser"
    override val pluginName = "Geyser"
    override val pluginId = "Geyser-Spigot"
    override val minSupportedVersion = "2.0"
    override val maxTestedVersion = "2.4"
    override val capabilities = setOf("bedrock")
    override fun enable() {}
}
