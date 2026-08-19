package dev.rlscoreboard.integration.util

/**
 * Splits a plugin-reported version string into its dot-separated numeric release tokens plus
 * whatever free-form suffix follows (design spec section 3: real plugins publish shapes like
 * `2.11.1`, `2.11.1-SNAPSHOT`, `5.2.6.6`, `1.7.3-b131`, `26.2-111`, and plenty of
 * non-standard ones). [suffix] is kept for display only - see [VersionRange] for why it never
 * affects range comparison.
 */
data class ParsedVersion(val raw: String, val release: List<Int>, val suffix: String?) {
    /** False for a string with no leading numeric release at all (`"unknown"`, `""`, a stray build hash, ...). */
    val isParseable: Boolean get() = release.isNotEmpty()

    override fun toString(): String = raw
}

/**
 * Parses and range-checks the version strings [Integration][dev.rlscoreboard.integration.Integration]s
 * report, replacing the earlier major-version-only comparison with real numeric-token
 * comparison (design spec sections 3/4/32). Two design choices worth calling out:
 *
 * 1. **The suffix never affects comparison.** `"2.11.1-SNAPSHOT"` compares as release `[2,
 *    11, 1]` - the `-SNAPSHOT`/`-b131`/`-111` part is build/pre-release metadata, not part of
 *    the "is this API surface within the tested range" question a min/max *release* range is
 *    actually asking. It's kept on [ParsedVersion.suffix] purely for diagnostics display.
 * 2. **`min` and `max` are compared with deliberately different padding.** A published range
 *    like `"5.0"`–`"5.4"` (this codebase's own LuckPerms range) means "the whole 5.4.x patch
 *    line is fine", not "only exactly 5.4.0 and below" - so [evaluate] zero-pads the *lower*
 *    bound (`min="5.0"` means `>= 5.0.0...`) but *truncates the detected version to the
 *    upper bound's own precision* before comparing against it (`max="5.4"` means "the first
 *    two components must not exceed 5.4", so a detected `5.4.117` compares as `5.4`, not as
 *    `5.4.117 > 5.4.0`). Getting this asymmetry wrong was caught before shipping by porting
 *    this exact algorithm to Python and checking it against every real range already declared
 *    in this codebase (LuckPerms 5.0-5.4 vs a real 5.4.117, PlaceholderAPI 2.11-2.11 vs a real
 *    2.11.6, etc.) - a naive symmetric zero-pad comparison flags *every one of those* as
 *    "above maximum", which is exactly the false-negative section 4 warns against.
 */
object VersionRange {

    private val LEADING_RELEASE = Regex("""^(\d+(?:\.\d+)*)(.*)$""")

    fun parse(version: String): ParsedVersion {
        val trimmed = version.trim()
        val match = LEADING_RELEASE.matchEntire(trimmed) ?: return ParsedVersion(trimmed, emptyList(), null)
        val release = match.groupValues[1].split('.').map { it.toInt() }
        val suffix = match.groupValues[2].trim('-', '+', '.', ' ').ifBlank { null }
        return ParsedVersion(trimmed, release, suffix)
    }

    /**
     * Where [detected] falls relative to a tested `[min, max]` release range (design spec
     * section 4). [BELOW_MINIMUM] and [ABOVE_MAXIMUM] are deliberately distinct outcomes, not
     * both lumped into one "unsupported" bucket - see [IntegrationStatus] for how each maps
     * to a lifecycle state. A version older than anything ever tested is genuinely risky
     * (APIs this integration calls may not exist yet); a version newer than anything tested
     * is usually fine and just hasn't been verified - conflating the two is exactly the
     * mistake section 4 calls out by name.
     */
    enum class RangeResult { WITHIN_RANGE, BELOW_MINIMUM, ABOVE_MAXIMUM, UNPARSEABLE }

    fun evaluate(detected: String, min: String, max: String): RangeResult {
        val d = parse(detected)
        if (!d.isParseable) return RangeResult.UNPARSEABLE

        val lo = parse(min)
        val hi = parse(max)

        if (lo.isParseable && cmpPadded(d.release, lo.release) < 0) return RangeResult.BELOW_MINIMUM
        if (hi.isParseable && cmpPadded(d.release.take(hi.release.size), hi.release) > 0) return RangeResult.ABOVE_MAXIMUM
        return RangeResult.WITHIN_RANGE
    }

    /** Standard release-token comparison, zero-padding whichever list is shorter. */
    private fun cmpPadded(a: List<Int>, b: List<Int>): Int {
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val cmp = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }
}
