package dev.rlscoreboard.integration.auraskills

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.integration.AbstractIntegration
import dev.rlscoreboard.leaderboard.datasource.AuraSkillsPowerLevelDataSource

/**
 * Registers the native "auraskills_powerlevel" leaderboard datasource
 * ([AuraSkillsPowerLevelDataSource]).
 *
 * `capabilities` deliberately only declares `"power"` today, not the full "skill levels /
 * skill XP / skill names" set from design spec section A's example - only power level is
 * actually wired up to a datasource right now. Declaring capabilities this integration
 * doesn't yet implement would be exactly the "claim support before it's verified" mistake
 * section C/D warns against; per-skill datasources are a documented future addition, not a
 * silent gap.
 */
class AuraSkillsIntegration(private val plugin: RLScoreboardPlugin) : AbstractIntegration() {
    override val id = "auraskills"
    override val pluginName = "AuraSkills"
    override val minSupportedVersion = "2.0"
    override val maxTestedVersion = "2.3"
    override val capabilities = setOf("power")

    override fun enable() {
        plugin.leaderboardEngine.dataSources.register(AuraSkillsPowerLevelDataSource())
    }
}
