package dev.rlscoreboard.integration

import org.bukkit.Bukkit

/**
 * Shared boilerplate for every built-in [Integration]: the mutable [status]/[detectedVersion]
 * fields [IntegrationManager] writes to, and the default "look the plugin up by [pluginId],
 * read its own `plugin.yml` version" implementations of [isInstalled]/[readVersion] that work
 * identically for every softdepend here - detection-only integrations (Jobs, Geyser,
 * Floodgate, WorldGuard, WorldEdit) get correct, real version-aware compatibility checking
 * from this base class alone, with no compiled API dependency needed just to answer "is it
 * installed and is the version in range".
 */
abstract class AbstractIntegration : Integration {

    override var status: IntegrationStatus = IntegrationStatus.NOT_INSTALLED
    override var detectedVersion: String? = null

    override fun isInstalled(): Boolean = Bukkit.getPluginManager().getPlugin(pluginId) != null

    override fun readVersion(): String? = Bukkit.getPluginManager().getPlugin(pluginId)?.pluginMeta?.version
}
