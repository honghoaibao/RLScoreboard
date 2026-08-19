package dev.rlscoreboard.placeholder

import dev.rlscoreboard.api.PlaceholderProvider
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves `%identifier%` placeholders in a line of text. Internal `%rl_*%` placeholders
 * and anything registered by other plugins through [dev.rlscoreboard.api.RLScoreboardAPI]
 * are resolved first; anything left over is handed to PlaceholderAPI if it's installed
 * (see [placeholderApiBridge]), otherwise it's left untouched rather than erroring out -
 * section 7 requires internal placeholders to keep working with PlaceholderAPI absent.
 */
class PlaceholderEngine {
    private val providers = ConcurrentHashMap<String, PlaceholderProvider>()

    /** Set by the PlaceholderAPI integration when (and only when) PlaceholderAPI is installed. */
    var placeholderApiBridge: ((Player?, String) -> String)? = null

    fun register(identifier: String, provider: PlaceholderProvider) {
        providers[identifier.lowercase()] = provider
    }

    fun unregister(identifier: String) {
        providers.remove(identifier.lowercase())
    }

    /** Resolves one identifier directly (no surrounding `%`), used by [dev.rlscoreboard.integration.placeholderapi.RLPlaceholderExpansion]. */
    fun resolveSingle(identifier: String, player: Player?): String? =
        providers[identifier.lowercase()]?.resolve(player)

    fun resolve(text: String, player: Player?): String {
        val afterInternal = PlaceholderParser.replace(text) { token -> providers[token.lowercase()]?.resolve(player) }
        val bridge = placeholderApiBridge
        return if (bridge != null && afterInternal.contains('%')) bridge(player, afterInternal) else afterInternal
    }
}
