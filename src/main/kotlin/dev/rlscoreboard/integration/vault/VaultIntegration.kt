package dev.rlscoreboard.integration.vault

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.Integration
import dev.rlscoreboard.leaderboard.datasource.EconomyDataSource
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit

/**
 * Registers the "economy" leaderboard datasource and `%rl_balance%` placeholder - but only
 * once Vault itself *and* a compatible economy plugin have both registered a service, so
 * nothing breaks (and nothing logs an error) when Vault is installed with no economy plugin
 * behind it.
 */
class VaultIntegration(private val plugin: RLScoreboardPlugin) : Integration {
    override val id = "Vault"

    override fun isAvailable(): Boolean =
        Bukkit.getPluginManager().getPlugin("Vault") != null &&
            Bukkit.getServicesManager().getRegistration(Economy::class.java) != null

    override fun enable() {
        val economy = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider ?: return
        plugin.leaderboardEngine.dataSources.register(EconomyDataSource(economy))
        plugin.placeholderEngine.register("rl_balance", PlaceholderProvider { player ->
            player?.let { runCatching { "%.2f".format(economy.getBalance(it)) }.getOrDefault("0.00") } ?: ""
        })
    }
}
