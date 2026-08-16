package dev.rlscoreboard.condition

import org.bukkit.configuration.ConfigurationSection

/**
 * Parses a `conditions:` (or single inline `condition:`) YAML section into a [ConditionSet].
 * Deliberately does not implement a general expression language (section 8 explicitly asks
 * for a safe parser, not a dangerous one) - only the fixed set of comparisons below.
 */
object ConditionParser {

    fun parse(section: ConfigurationSection?): ConditionSet {
        if (section == null) return ConditionSet.EMPTY
        val list = mutableListOf<Condition>()

        section.getStringList("world").takeIf { it.isNotEmpty() }
            ?.let { list += Condition.WorldIn(it) }
        section.getStringList("permission").takeIf { it.isNotEmpty() }
            ?.let { list += Condition.PermissionAll(it) }
        section.getStringList("gamemode").takeIf { it.isNotEmpty() }
            ?.let { list += Condition.Gamemode(it) }

        section.getConfigurationSection("health")?.let {
            list += Condition.HealthCompare(Operator.parse(it.getString("operator", "==")!!), it.getDouble("value"))
        }
        (section.getConfigurationSection("online_players") ?: section.getConfigurationSection("online"))?.let {
            list += Condition.OnlineCountCompare(Operator.parse(it.getString("operator", "==")!!), it.getInt("value"))
        }

        // Single inline placeholder condition:
        //   conditions: { placeholder: "%x%", operator: ">=", value: "10000" }
        if (section.isString("placeholder")) {
            list += Condition.PlaceholderCompare(
                section.getString("placeholder")!!,
                Operator.parse(section.getString("operator", "==")!!),
                section.getString("value", "") ?: ""
            )
        }

        // Multiple placeholder conditions:
        //   conditions: { placeholders: [ {placeholder, operator, value}, ... ] }
        for (raw in section.getMapList("placeholders")) {
            val placeholder = raw["placeholder"]?.toString() ?: continue
            val operator = Operator.parse(raw["operator"]?.toString() ?: "==")
            val value = raw["value"]?.toString() ?: ""
            list += Condition.PlaceholderCompare(placeholder, operator, value)
        }

        section.getConfigurationSection("custom")?.let { customSection ->
            val providerId = customSection.getString("provider") ?: return@let
            val argsSection = customSection.getConfigurationSection("args")
            val args = argsSection?.getKeys(false)
                ?.associateWith { key -> argsSection.getString(key, "") ?: "" }
                ?: emptyMap()
            list += Condition.Custom(providerId, args)
        }

        return ConditionSet(list)
    }
}
