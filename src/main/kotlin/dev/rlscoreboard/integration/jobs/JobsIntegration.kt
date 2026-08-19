package dev.rlscoreboard.integration.jobs

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.AbstractIntegration
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Bridges Jobs Reborn's *own* PlaceholderAPI expansion (registered internally by Jobs Reborn
 * itself as `jobsr` whenever both it and PlaceholderAPI are present - confirmed against the
 * plugin's own wiki, `github.com/Zrips/Jobs/wiki/Placeholders`, not guessed) into
 * RLScoreboard-branded `%rl_job_*%` placeholders (design spec section 8), rather than adding a
 * direct compiled dependency on Jobs Reborn's own API. That's a deliberate choice, not a
 * shortcut: the only published Jobs Reborn API artifact pulls in hard transitive dependencies
 * (mcMMO, WorldGuard, WorldEdit, WildStackerAPI, StackMob) that broke dependency resolution
 * entirely on the first real build - see the main README's
 * "What broke on the first real build" - so this integration never touches Jobs Reborn's own
 * classes at all, compiled or otherwise; it only ever calls into `PlaceholderAPI`, an
 * already-trusted dependency.
 *
 * Every `%rl_job_*%` placeholder targets the player's **first currently-active job**
 * (Jobs Reborn's own `_1` index) - the common single-job case. A multi-job server wanting a
 * specific job's numbers should use Jobs Reborn's raw `%jobsr_user_jlevel_2%`-style
 * placeholders directly (already usable in any board line today via
 * [dev.rlscoreboard.integration.placeholderapi.PlaceholderAPIIntegration]'s fallback bridge) -
 * this integration is a convenience shortcut for the common case, not a full replacement.
 *
 * **Not implemented**: `%rl_job_income%` from the original wishlist. There is no Jobs
 * Reborn placeholder for a running "total income" figure - job payments go straight through
 * Vault's economy balance, which isn't job-specific, and the closest job-specific number
 * (`%jobsr_user_boost_1_money%`) is a payment *multiplier*, not an income total. Rather than
 * force a placeholder onto a concept Jobs Reborn doesn't actually track this way, it's left
 * out - see ROADMAP.md.
 */
class JobsIntegration(private val plugin: RLScoreboardPlugin) : AbstractIntegration() {
    override val id = "jobs"
    override val pluginName = "Jobs Reborn"
    override val pluginId = "Jobs"
    override val minSupportedVersion = "5.0"
    override val maxTestedVersion = "5.2"
    override val capabilities = setOf("jobs_placeholders")

    override fun enable(): Set<String> {
        register("rl_job", "%jobsr_user_job_1%")
        register("rl_job_level", "%jobsr_user_jlevel_1%")
        register("rl_job_exp", "%jobsr_user_jexp_rounded_1%")
        register("rl_job_points", "%jobsr_user_points_fixed%")

        plugin.placeholderEngine.register("rl_job_progress", PlaceholderProvider { player ->
            if (!papiPresent() || player == null) return@PlaceholderProvider ""
            val current = resolveNumeric(player, "%jobsr_user_jexp_1%") ?: return@PlaceholderProvider ""
            val max = resolveNumeric(player, "%jobsr_user_jmaxexp_1%")?.takeIf { it > 0.0 } ?: return@PlaceholderProvider ""
            "%.1f".format((current / max) * 100.0)
        })

        return capabilities
    }

    /** Registers `%<key>%` as a direct passthrough of [papiToken], resolved fresh on every call - never a stale snapshot from enable() time. Empty string (never the raw token) if PlaceholderAPI itself isn't present or hasn't resolved it. */
    private fun register(key: String, papiToken: String) {
        plugin.placeholderEngine.register(key, PlaceholderProvider { player ->
            if (!papiPresent() || player == null) return@PlaceholderProvider ""
            val resolved = runCatching { PlaceholderAPI.setPlaceholders(player, papiToken) }.getOrNull() ?: ""
            if (resolved == papiToken) "" else resolved // PAPI echoes the token back unresolved if Jobs isn't hooked in - never show that raw token to a player.
        })
    }

    private fun resolveNumeric(player: Player, papiToken: String): Double? {
        val resolved = runCatching { PlaceholderAPI.setPlaceholders(player, papiToken) }.getOrNull() ?: return null
        return resolved.toDoubleOrNull()
    }

    /** PlaceholderAPI is a *different* optional integration - these placeholders only do anything with both installed, and must never NoClassDefFoundError if PAPI happens to be absent despite Jobs Reborn being present. */
    private fun papiPresent(): Boolean = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
}
