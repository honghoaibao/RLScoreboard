package dev.rlscoreboard.core

import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Per-player render state, kept so [BoardRenderer] can diff against the previous frame instead of rebuilding every tick. */
class PlayerSession(val playerId: UUID) {
    var scoreboard: Scoreboard? = null
    var activeBoardId: String? = null
    var lastRenderedLines: List<String> = emptyList()
    var lastTitle: String = ""
}

class PlayerSessionManager {
    private val sessions = ConcurrentHashMap<UUID, PlayerSession>()

    fun sessionFor(player: Player): PlayerSession =
        sessions.getOrPut(player.uniqueId) { PlayerSession(player.uniqueId) }

    fun remove(player: Player) {
        sessions.remove(player.uniqueId)
    }

    fun clear() = sessions.clear()
}
