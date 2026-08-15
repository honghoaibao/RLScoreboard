package dev.rlscoreboard.integration.luckperms

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.Integration
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Bukkit

/** Adds `%rl_rank%` (primary group) and `%rl_prefix%` (LuckPerms prefix) placeholders. */
class LuckPermsIntegration(private val plugin: RLScoreboardPlugin) : Integration {
    override val id = "LuckPerms"

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("LuckPerms") != null

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
