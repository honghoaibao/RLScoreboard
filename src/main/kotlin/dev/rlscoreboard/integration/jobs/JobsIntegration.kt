package dev.rlscoreboard.integration.jobs

import dev.rlscoreboard.integration.Integration
import org.bukkit.Bukkit

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
 * integration exists purely so startup logging correctly reports Jobs as detected.
 */
class JobsIntegration : Integration {
    override val id = "Jobs"
    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("Jobs") != null
    override fun enable() {}
}
