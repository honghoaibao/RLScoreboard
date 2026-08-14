package dev.rlscoreboard.config

/**
 * Small, deliberately boring validation helpers (section 23 - config must never be able to
 * crash the plugin or inject anything dangerous; malformed entries are skipped and logged,
 * not evaluated as code).
 */
object ConfigValidator {
    private val ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,64}")

    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    fun sanitizeId(id: String): String = id.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
}
