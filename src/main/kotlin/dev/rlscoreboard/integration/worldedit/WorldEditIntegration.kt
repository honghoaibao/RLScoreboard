package dev.rlscoreboard.integration.worldedit

import dev.rlscoreboard.integration.AbstractIntegration

/**
 * Detection-only. Design spec section B lists WorldEdit purely as a "future/editor support"
 * extension point (e.g. a future `/rlscoreboard editor` selecting a hologram leaderboard's
 * location via a WorldEdit-style wand) - there is no current RLScoreboard feature that needs
 * WorldEdit's API, so `capabilities` stays empty rather than declaring something unbuilt.
 * Kept as its own integration (not folded into [dev.rlscoreboard.integration.worldguard.WorldGuardIntegration])
 * because the two plugins are installed and versioned independently even though they usually
 * travel together.
 */
class WorldEditIntegration : AbstractIntegration() {
    override val id = "worldedit"
    override val pluginName = "WorldEdit"
    override val minSupportedVersion = "7.0"
    override val maxTestedVersion = "7.3"
    override val capabilities: Set<String> = emptySet()
    override fun enable(): Set<String> = capabilities
}
