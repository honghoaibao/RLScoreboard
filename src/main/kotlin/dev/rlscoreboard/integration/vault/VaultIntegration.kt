package dev.rlscoreboard.integration.vault

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.AbstractIntegration
import dev.rlscoreboard.integration.IntegrationStatus
import dev.rlscoreboard.leaderboard.datasource.EconomyDataSource
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit

/**
 * Registers the "economy" leaderboard datasource and `%rl_balance%` placeholder.
 *
 * Deliberately illustrates design spec section D ("Vault present, Economy provider absent"):
 * [isInstalled] only checks for the Vault plugin itself, so `status` can reach
 * [IntegrationStatus.SUPPORTED] (and [enable] runs) even with no economy plugin behind it -
 * that's correct, since Vault itself is genuinely present. What changes is [hasCapability]:
 * it re-checks the live `Economy` service registration for the `"economy"` capability on
 * every call, so a server that has Vault but no economy plugin (or unloads its economy
 * plugin later) correctly reports the capability unavailable without RLScoreboard needing to
 * re-run detection. `%rl_balance%`'s placeholder provider does the same live re-lookup rather
 * than closing over a single `Economy` instance from startup, so it degrades to `""` the
 * moment the provider disappears instead of holding a stale reference.
 */
class VaultIntegration(private val plugin: RLScoreboardPlugin) : AbstractIntegration() {
    override val id = "vault"
    override val pluginName = "Vault"
    override val minSupportedVersion = "1.7"
    override val maxTestedVersion = "1.7"
    override val capabilities = setOf("economy")

    override fun hasCapability(capability: String): Boolean {
        if (status != IntegrationStatus.SUPPORTED || capability !in capabilities) return false
        return economyProvider() != null
    }

    override fun enable() {
        // Register the datasource only if an economy provider is live *right now* - if one
        // registers later (economy plugin loaded after RLScoreboard), it'll be picked up on
        // the next `/rlscoreboard reload` restart of integration loading, consistent with how
        // every other integration here re-detects on reload rather than watching
        // ServicesManager events.
        economyProvider()?.let { economy ->
            plugin.leaderboardEngine.dataSources.register(EconomyDataSource(economy))
        }

        plugin.placeholderEngine.register("rl_balance", PlaceholderProvider { player ->
            val economy = economyProvider() ?: return@PlaceholderProvider ""
            player?.let { runCatching { "%.2f".format(economy.getBalance(it)) }.getOrDefault("0.00") } ?: ""
        })
    }

    private fun economyProvider(): Economy? = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
}
