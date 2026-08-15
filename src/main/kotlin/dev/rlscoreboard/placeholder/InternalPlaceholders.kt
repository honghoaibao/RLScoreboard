package dev.rlscoreboard.placeholder

import dev.rlscoreboard.api.PlaceholderProvider
import org.bukkit.Bukkit

/** Registers the built-in `%rl_*%` placeholders from section 7 of the design spec - always available, no dependency needed. */
object InternalPlaceholders {

    fun registerAll(engine: PlaceholderEngine) {
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
    }
}
