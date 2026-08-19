package dev.rlscoreboard.integration.util

import dev.rlscoreboard.integration.util.VersionRange.RangeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers every version-string shape design spec section 32 explicitly calls out
 * (`2.11.1-SNAPSHOT`, `5.2.6.6`, `1.7.3-b131`, `26.2-111`, `unknown`, empty), plus every real
 * tested-version range already declared by a built-in [dev.rlscoreboard.integration.Integration]
 * in this codebase (LuckPerms, PlaceholderAPI, VaultAPI, AuraSkills, Geyser) - so a change to
 * [VersionRange] that would silently mis-classify one of this project's own real integrations
 * fails a test, not just a "looks right" read-through.
 *
 * These exact cases were verified once already via a Python port of the algorithm (see
 * [VersionRange]'s own KDoc for why: this environment has no Kotlin compiler to run this file
 * against) before the Kotlin was written - this test suite is that same verification, now
 * expressed as real, re-runnable Kotlin/JUnit rather than a one-off script.
 */
class VersionRangeTest {

    // ---- parse() ----

    @Test
    fun `parses a plain three-component release`() {
        val parsed = VersionRange.parse("2.11.1")
        assertEquals(listOf(2, 11, 1), parsed.release)
        assertEquals(null, parsed.suffix)
        assertTrue(parsed.isParseable)
    }

    @Test
    fun `separates a SNAPSHOT suffix from the release`() {
        val parsed = VersionRange.parse("2.11.1-SNAPSHOT")
        assertEquals(listOf(2, 11, 1), parsed.release)
        assertEquals("SNAPSHOT", parsed.suffix)
    }

    @Test
    fun `handles a four-component release`() {
        val parsed = VersionRange.parse("5.2.6.6")
        assertEquals(listOf(5, 2, 6, 6), parsed.release)
        assertEquals(null, parsed.suffix)
    }

    @Test
    fun `separates a build-id suffix like b131`() {
        val parsed = VersionRange.parse("1.7.3-b131")
        assertEquals(listOf(1, 7, 3), parsed.release)
        assertEquals("b131", parsed.suffix)
    }

    @Test
    fun `handles a two-component release with a numeric suffix`() {
        val parsed = VersionRange.parse("26.2-111")
        assertEquals(listOf(26, 2), parsed.release)
        assertEquals("111", parsed.suffix)
    }

    @Test
    fun `a string with no leading digit is not parseable`() {
        val parsed = VersionRange.parse("unknown")
        assertTrue(parsed.release.isEmpty())
        assertFalse(parsed.isParseable)
    }

    @Test
    fun `an empty string is not parseable`() {
        assertFalse(VersionRange.parse("").isParseable)
    }

    // ---- evaluate() - the section-32 cases plus every real range in this codebase ----

    @Test
    fun `a patch release within a tested minor line is within range`() {
        // LuckPermsIntegration's own real range: tested 5.0-5.4, a real LuckPerms build number.
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("5.4.117", "5.0", "5.4"))
    }

    @Test
    fun `a newer minor version than the tested maximum is ABOVE_MAXIMUM, not INCOMPATIBLE`() {
        // The exact distinction design spec section 4 exists to enforce.
        assertEquals(RangeResult.ABOVE_MAXIMUM, VersionRange.evaluate("5.9.0", "5.0", "5.4"))
    }

    @Test
    fun `an older version than the tested minimum is BELOW_MINIMUM`() {
        assertEquals(RangeResult.BELOW_MINIMUM, VersionRange.evaluate("4.9.0", "5.0", "5.4"))
    }

    @Test
    fun `a SNAPSHOT suffix does not affect range comparison`() {
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("2.11.1-SNAPSHOT", "2.11", "2.11"))
    }

    @Test
    fun `a build-id suffix does not affect range comparison`() {
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("1.7.3-b131", "1.7", "1.7"))
    }

    @Test
    fun `a four-component version compares correctly against a two-component range`() {
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("5.2.6.6", "5.0", "5.2"))
    }

    @Test
    fun `a two-component version with a numeric suffix is within its matching range`() {
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("26.2-111", "26.0", "26.2"))
    }

    @Test
    fun `an unparseable detected version is reported as such, not silently allowed or blocked`() {
        assertEquals(RangeResult.UNPARSEABLE, VersionRange.evaluate("unknown", "2.0", "2.4"))
        assertEquals(RangeResult.UNPARSEABLE, VersionRange.evaluate("", "2.0", "2.4"))
    }

    @Test
    fun `matches docs INTEGRATIONS md's AuraSkills example`() {
        assertEquals(RangeResult.ABOVE_MAXIMUM, VersionRange.evaluate("3.0.1", "2.0", "2.3"))
    }

    @Test
    fun `matches PlaceholderAPI's own real tested range`() {
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("2.11.6", "2.11", "2.11"))
    }

    @Test
    fun `matches VaultAPI's own real tested range`() {
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("1.7.3", "1.7", "1.7"))
    }

    @Test
    fun `matches Geyser's documented range in docs INTEGRATIONS md`() {
        assertEquals(RangeResult.WITHIN_RANGE, VersionRange.evaluate("2.4.0", "2.0", "2.4"))
        assertEquals(RangeResult.ABOVE_MAXIMUM, VersionRange.evaluate("2.5.0", "2.0", "2.4"))
    }

    @Test
    fun `an old-style Bukkit R-release SNAPSHOT version outside a modern range is ABOVE_MAXIMUM`() {
        // Sanity check against a genuinely different-shaped, older version-string style
        // (org.bukkit-era "1.13.1-R0.1-SNAPSHOT" naming) - the release ("1.13.1") is what's
        // compared; the "-R0.1-SNAPSHOT" part is all suffix.
        assertEquals(RangeResult.ABOVE_MAXIMUM, VersionRange.evaluate("1.13.1-R0.1-SNAPSHOT", "1.7", "1.7"))
    }
}
