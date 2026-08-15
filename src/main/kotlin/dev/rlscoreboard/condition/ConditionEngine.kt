package dev.rlscoreboard.condition

import dev.rlscoreboard.api.ConditionProvider
import dev.rlscoreboard.placeholder.PlaceholderEngine
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/** Evaluates [ConditionSet]s against a player. Unknown/failed custom providers pass open. */
class ConditionEngine(private val placeholders: PlaceholderEngine) {
    private val customProviders = ConcurrentHashMap<String, ConditionProvider>()

    fun registerProvider(id: String, provider: ConditionProvider) {
        customProviders[id.lowercase()] = provider
    }

    fun evaluate(set: ConditionSet, player: Player): Boolean =
        set.conditions.all { evaluateOne(it, player) }

    private fun evaluateOne(condition: Condition, player: Player): Boolean = when (condition) {
        is Condition.WorldIn -> condition.worlds.any { it.equals(player.world.name, ignoreCase = true) }
        is Condition.PermissionAll -> condition.permissions.all { player.hasPermission(it) }
        is Condition.Gamemode -> condition.modes.any { it.equals(player.gameMode.name, ignoreCase = true) }
        is Condition.HealthCompare -> compareNumbers(player.health, condition.operator, condition.value)
        is Condition.OnlineCountCompare ->
            compareNumbers(Bukkit.getOnlinePlayers().size.toDouble(), condition.operator, condition.value.toDouble())
        is Condition.PlaceholderCompare -> {
            val resolved = placeholders.resolve(condition.placeholder, player)
            compareText(resolved, condition.operator, condition.value)
        }
        is Condition.Custom -> runCatching {
            customProviders[condition.providerId.lowercase()]?.evaluate(player, condition.args) ?: true
        }.getOrDefault(true)
    }

    private fun compareNumbers(actual: Double, operator: Operator, expected: Double): Boolean = when (operator) {
        Operator.EQUALS -> actual == expected
        Operator.NOT_EQUALS -> actual != expected
        Operator.GREATER_THAN -> actual > expected
        Operator.LESS_THAN -> actual < expected
        Operator.GREATER_OR_EQUAL -> actual >= expected
        Operator.LESS_OR_EQUAL -> actual <= expected
        Operator.CONTAINS, Operator.STARTS_WITH, Operator.ENDS_WITH -> false
    }

    private fun compareText(actual: String, operator: Operator, expected: String): Boolean {
        val actualNum = actual.toDoubleOrNull()
        val expectedNum = expected.toDoubleOrNull()
        if (actualNum != null && expectedNum != null) {
            return compareNumbers(actualNum, operator, expectedNum)
        }
        return when (operator) {
            Operator.EQUALS -> actual.equals(expected, ignoreCase = true)
            Operator.NOT_EQUALS -> !actual.equals(expected, ignoreCase = true)
            Operator.CONTAINS -> actual.contains(expected, ignoreCase = true)
            Operator.STARTS_WITH -> actual.startsWith(expected, ignoreCase = true)
            Operator.ENDS_WITH -> actual.endsWith(expected, ignoreCase = true)
            Operator.GREATER_THAN -> actual > expected
            Operator.LESS_THAN -> actual < expected
            Operator.GREATER_OR_EQUAL -> actual >= expected
            Operator.LESS_OR_EQUAL -> actual <= expected
        }
    }
}
