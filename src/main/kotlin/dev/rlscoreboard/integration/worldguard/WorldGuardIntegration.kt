package dev.rlscoreboard.integration.worldguard

import dev.rlscoreboard.integration.AbstractIntegration

/**
 * Detection-only for now (design spec sections B/C list WorldGuard as "Optional", not yet
 * "Supported" with real capabilities). `capabilities` is deliberately empty: a real
 * region-based condition (e.g. `worldguard-region: spawn` in a board's `conditions:` block)
 * would need `com.sk89q.worldguard:worldguard-bukkit` as a `compileOnly` dependency and its
 * `RegionContainer`/`RegionQuery` API, whose exact shape differs meaningfully between
 * WorldGuard 6.x and 7.x - getting that wrong is the same class of mistake that broke the
 * build on the native Jobs Reborn attempt (see JobsIntegration/README "What broke on the
 * first real build"), so it isn't attempted here without a live WorldGuard install and CI
 * run to verify against.
 *
 * Still worth detecting and reporting: knowing WorldGuard is present is useful diagnostic
 * information on its own (`/rlscoreboard integrations`, docs/INTEGRATIONS.md), and this class
 * is the extension point a future `"region"` capability gets added to.
 */
class WorldGuardIntegration : AbstractIntegration() {
    override val id = "worldguard"
    override val pluginName = "WorldGuard"
    override val minSupportedVersion = "7.0"
    override val maxTestedVersion = "7.0"
    override val capabilities: Set<String> = emptySet()
    override fun enable(): Set<String> = capabilities
}
