package dev.rlscoreboard.integration.luckperms

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.AbstractIntegration
import net.luckperms.api.LuckPermsProvider

/**
 * Adds `%rl_rank%` (primary group), `%rl_prefix%`, and `%rl_suffix%` placeholders.
 *
 * The reference example for [Integration.enable][dev.rlscoreboard.integration.Integration.enable]'s
 * per-capability isolation: `"rank"`, `"prefix"`, and `"suffix"` are registered in three
 * separate `runCatching` blocks rather than one try/catch around the whole method, so if a
 * future LuckPerms release ever removed/renamed just the suffix API (for example), this
 * integration would still come up [dev.rlscoreboard.integration.IntegrationStatus.PARTIALLY_SUPPORTED]
 * with rank and prefix both working, instead of the whole integration going down as
 * [dev.rlscoreboard.integration.IntegrationStatus.ERROR] over one broken capability.
 */
class LuckPermsIntegration(private val plugin: RLScoreboardPlugin) : AbstractIntegration() {
    override val id = "luckperms"
    override val pluginName = "LuckPerms"
    override val minSupportedVersion = "5.0"
    override val maxTestedVersion = "5.4"
    override val capabilities = setOf("rank", "prefix", "suffix")

    override fun enable(): Set<String> {
        val api = LuckPermsProvider.get()
        val active = mutableSetOf<String>()

        runCatching {
            plugin.placeholderEngine.register("rl_rank", PlaceholderProvider { player ->
                player?.let { p ->
                    val group = api.userManager.getUser(p.uniqueId)?.primaryGroup ?: ""
                    group.replaceFirstChar { c -> c.uppercase() }
                } ?: ""
            })
        }.onSuccess { active += "rank" }

        runCatching {
            plugin.placeholderEngine.register("rl_prefix", PlaceholderProvider { player ->
                player?.let { p -> api.userManager.getUser(p.uniqueId)?.cachedData?.metaData?.prefix ?: "" } ?: ""
            })
        }.onSuccess { active += "prefix" }

        runCatching {
            plugin.placeholderEngine.register("rl_suffix", PlaceholderProvider { player ->
                player?.let { p -> api.userManager.getUser(p.uniqueId)?.cachedData?.metaData?.suffix ?: "" } ?: ""
            })
        }.onSuccess { active += "suffix" }

        return active
    }
}
