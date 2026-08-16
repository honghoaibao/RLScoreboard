package dev.rlscoreboard.integration.luckperms

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.AbstractIntegration
import net.luckperms.api.LuckPermsProvider

/** Adds `%rl_rank%` (primary group) and `%rl_prefix%` (LuckPerms prefix) placeholders. */
class LuckPermsIntegration(private val plugin: RLScoreboardPlugin) : AbstractIntegration() {
    override val id = "luckperms"
    override val pluginName = "LuckPerms"
    override val minSupportedVersion = "5.0"
    override val maxTestedVersion = "5.4"
    override val capabilities = setOf("rank", "prefix")

    override fun enable() {
        val api = LuckPermsProvider.get()

        plugin.placeholderEngine.register("rl_rank", PlaceholderProvider { player ->
            player?.let { p ->
                val group = api.userManager.getUser(p.uniqueId)?.primaryGroup ?: ""
                group.replaceFirstChar { c -> c.uppercase() }
            } ?: ""
        })

        plugin.placeholderEngine.register("rl_prefix", PlaceholderProvider { player ->
            player?.let { p -> api.userManager.getUser(p.uniqueId)?.cachedData?.metaData?.prefix ?: "" } ?: ""
        })
    }
}
