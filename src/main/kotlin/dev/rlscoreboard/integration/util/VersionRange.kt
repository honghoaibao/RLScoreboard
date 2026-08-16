package dev.rlscoreboard.integration.util

/**
 * Major-version-range compatibility checking shared by every [dev.rlscoreboard.integration.Integration]
 * (design spec section C). Every tested-version range in this codebase's docs/comments is
 * already expressed as a major-version band ("5.x", "2.x", "7.x") rather than an exact
 * build, so that's the granularity checked here - a minor/patch bump within the same major
 * version is assumed compatible (that's what "tested against 5.4, works with 5.x" means in
 * practice for these plugins' APIs), while a major version bump is not, since that's exactly
 * when Bukkit-plugin APIs tend to make breaking changes.
 */
object VersionRange {

    private val LEADING_NUMBER = Regex("""(\d+)(?:\.(\d+))?""")

    /**
     * True if [detected] falls within `[min, max]` by major version, inclusive. A version
     * string that can't be parsed (custom build strings, git-hash suffixes, etc.) is treated
     * as compatible rather than blocking the integration - an unrecognised format is
     * something to log and let an admin judge for themselves, not a reason for RLScoreboard
     * to silently disable a working integration on a false negative.
     */
    fun isCompatible(detected: String, min: String, max: String): Boolean {
        val detectedMajor = majorOf(detected) ?: return true
        val minMajor = majorOf(min) ?: return true
        val maxMajor = majorOf(max) ?: return true
        return detectedMajor in minMajor..maxMajor
    }

    private fun majorOf(version: String): Int? = LEADING_NUMBER.find(version)?.groupValues?.get(1)?.toIntOrNull()
}
