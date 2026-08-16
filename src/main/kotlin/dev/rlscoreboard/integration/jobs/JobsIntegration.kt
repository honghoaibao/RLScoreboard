package dev.rlscoreboard.integration.jobs

import dev.rlscoreboard.integration.AbstractIntegration

/**
 * Detection-only, on purpose. A native "jobs_totallevel" datasource was tried and reverted:
 * com.github.Zrips:Jobs's JitPack-published POM pulls in hard transitive dependencies
 * (mcMMO, WorldGuard, WorldEdit, WildStackerAPI, StackMob) that don't resolve from any
 * repository configured in this project, which broke the whole build - see build.gradle.kts
 * and README "Residual risk" for the full story.
 *
 * Jobs Reborn ships its own PlaceholderAPI expansion, so once both PlaceholderAPI and Jobs
 * are installed, `%jobs_*%` placeholders already work in any board or leaderboard line for
 * free via [dev.rlscoreboard.integration.placeholderapi.PlaceholderAPIIntegration] - this
 * integration exists purely so startup logging/`/rlscoreboard integrations` correctly
 * reports Jobs as detected. `capabilities` is declared as `"jobs_placeholders"`, not a plain
 * `"jobs"`, precisely so nothing in RLScoreboard could mistake it for a native sortable
 * datasource capability that doesn't exist.
 */
class JobsIntegration : AbstractIntegration() {
    override val id = "jobs"
    override val pluginName = "Jobs Reborn"
    override val pluginId = "Jobs"
    override val minSupportedVersion = "5.0"
    override val maxTestedVersion = "5.2"
    override val capabilities = setOf("jobs_placeholders")
    override fun enable() {}
}
