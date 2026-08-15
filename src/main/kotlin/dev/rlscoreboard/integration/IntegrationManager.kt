package dev.rlscoreboard.integration

import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects each optional [Integration] at startup and logs the result in the clean,
 * predictable format from section 4 of the design spec - no stack traces, no spam when
 * something simply isn't installed.
 */
class IntegrationManager(private val plugin: Plugin) {
    private val enabled = ConcurrentHashMap<String, Boolean>()

    fun loadAll(integrations: List<Integration>) {
        plugin.logger.info("Loading integrations...")
        plugin.logger.info("\u2713 Core")

        for (integration in integrations) {
            val available = runCatching { integration.isAvailable() }.getOrDefault(false)
            enabled[integration.id.lowercase()] = available

            if (available) {
                runCatching { integration.enable() }
                    .onFailure { plugin.logger.warning("Failed to enable ${integration.id} integration: ${it.message}") }
                plugin.logger.info("\u2713 ${integration.id}")
            } else {
                plugin.logger.info("- ${integration.id} not installed")
            }
        }

        plugin.logger.info("Initialization complete.")
    }

    fun isEnabled(id: String): Boolean = enabled[id.lowercase()] == true
}
