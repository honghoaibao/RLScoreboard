package dev.rlscoreboard.integration

/** One optional third-party integration. Never depended on directly by core engines - see section 4/22. */
interface Integration {
    val id: String
    fun isAvailable(): Boolean
    fun enable()
}
