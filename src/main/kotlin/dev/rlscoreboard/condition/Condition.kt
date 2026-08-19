package dev.rlscoreboard.condition

/**
 * A single evaluable condition. See [ConditionParser] for the YAML shapes these are
 * parsed from and section 8 of the design spec for the full list of supported operators.
 */
sealed interface Condition {
    data class WorldIn(val worlds: List<String>) : Condition
    data class PermissionAll(val permissions: List<String>) : Condition
    data class Gamemode(val modes: List<String>) : Condition
    data class HealthCompare(val operator: Operator, val value: Double) : Condition
    data class OnlineCountCompare(val operator: Operator, val value: Int) : Condition
    data class PlaceholderCompare(val placeholder: String, val operator: Operator, val value: String) : Condition
    data class Custom(val providerId: String, val args: Map<String, String>) : Condition
}

enum class Operator {
    EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH,
    GREATER_THAN, LESS_THAN, GREATER_OR_EQUAL, LESS_OR_EQUAL;

    companion object {
        fun parse(raw: String): Operator = when (raw.trim().lowercase()) {
            "==", "equals" -> EQUALS
            "!=", "not_equals" -> NOT_EQUALS
            "contains" -> CONTAINS
            "starts_with" -> STARTS_WITH
            "ends_with" -> ENDS_WITH
            ">", "greater_than" -> GREATER_THAN
            "<", "less_than" -> LESS_THAN
            ">=", "greater_or_equal", "greater_than_or_equal" -> GREATER_OR_EQUAL
            "<=", "less_or_equal", "less_than_or_equal" -> LESS_OR_EQUAL
            else -> EQUALS
        }
    }
}

/**
 * A set of conditions that must ALL pass (AND semantics) for a board/line to be shown.
 * An empty set always passes - this is what a board/line with no `conditions:` block gets.
 */
data class ConditionSet(val conditions: List<Condition> = emptyList()) {
    companion object {
        val EMPTY = ConditionSet(emptyList())
    }
}
