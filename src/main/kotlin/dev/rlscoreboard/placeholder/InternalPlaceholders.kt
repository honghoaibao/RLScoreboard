package dev.rlscoreboard.placeholder

import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.IntegrationManager
import dev.rlscoreboard.integration.floodgate.FloodgateIntegration
import org.bukkit.Bukkit
import java.util.UUID

/** Registers the built-in `%rl_*%` placeholders from section 7 of the design spec - always available, no dependency needed. */
object InternalPlaceholders {

    fun registerAll(engine: PlaceholderEngine, integrationManager: IntegrationManager) {
        engine.register("rl_player_name", PlaceholderProvider { it?.name ?: "" })
        engine.register("rl_player_uuid", PlaceholderProvider { it?.uniqueId?.toString() ?: "" })
        engine.register("rl_world", PlaceholderProvider { it?.world?.name ?: "" })
        engine.register("rl_x", PlaceholderProvider { it?.location?.blockX?.toString() ?: "" })
        engine.register("rl_y", PlaceholderProvider { it?.location?.blockY?.toString() ?: "" })
        engine.register("rl_z", PlaceholderProvider { it?.location?.blockZ?.toString() ?: "" })
        engine.register("rl_online", PlaceholderProvider { Bukkit.getOnlinePlayers().size.toString() })
        engine.register("rl_max_players", PlaceholderProvider { Bukkit.getMaxPlayers().toString() })
        engine.register("rl_ping", PlaceholderProvider { it?.ping?.toString() ?: "" })
        engine.register("rl_gamemode", PlaceholderProvider { it?.gameMode?.name ?: "" })

        registerPlatformPlaceholders(engine, integrationManager)
    }

    /**
     * `%rl_platform%` / `%rl_is_bedrock%` / `%rl_is_java%` (design spec section 7). Bedrock
     * detection specifically needs Floodgate's `bedrock_identity` capability
     * ([FloodgateIntegration]'s UUID-based heuristic - see its own KDoc for exactly how and
     * why it's a heuristic, not the real `FloodgateApi`) - Geyser alone, without Floodgate,
     * usually means the Bedrock player is connecting through a real linked Java/Xbox account,
     * whose UUID is indistinguishable from any other Java player's. Without Floodgate live,
     * every player reports as Java - the honest default when RLScoreboard genuinely has no
     * signal either way, not a bug.
     */
    private fun registerPlatformPlaceholders(engine: PlaceholderEngine, integrationManager: IntegrationManager) {
        fun isBedrock(uuid: UUID): Boolean =
            integrationManager.hasCapability("floodgate", "bedrock_identity") && FloodgateIntegration.isLikelyBedrockPlayer(uuid)

        engine.register("rl_platform", PlaceholderProvider { player ->
            player?.let { if (isBedrock(it.uniqueId)) "Bedrock" else "Java" } ?: ""
        })
        engine.register("rl_is_bedrock", PlaceholderProvider { player ->
            player?.let { isBedrock(it.uniqueId).toString() } ?: ""
        })
        engine.register("rl_is_java", PlaceholderProvider { player ->
            player?.let { (!isBedrock(it.uniqueId)).toString() } ?: ""
        })
    }
}
