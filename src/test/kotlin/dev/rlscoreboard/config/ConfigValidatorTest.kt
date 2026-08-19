package dev.rlscoreboard.config

import org.bukkit.configuration.file.YamlConfiguration
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ConfigValidator.hasValidColorCodes]/[ConfigValidator.isRecognizedOperator]/
 * [ConfigValidator.isRecognizedAnimationPreset] are pure string logic - no Bukkit server
 * needed to exercise them. [ConfigValidator.scanSection] is tested against real
 * [YamlConfiguration] instances built from in-memory YAML text: `YamlConfiguration` is a
 * self-contained, real implementation bundled directly in the Bukkit/Paper API jar (not a
 * server-runtime-dependent interface), so this works without a mock server - see
 * `build.gradle.kts`'s `testImplementation.extendsFrom(compileOnly)` for how the API jar gets
 * onto the test classpath in the first place.
 */
class ConfigValidatorTest {

    // ---- hasValidColorCodes ----

    @Test
    fun `plain text with no ampersand is always valid`() {
        assertTrue(ConfigValidator.hasValidColorCodes("Just plain text"))
    }

    @Test
    fun `every legacy color and format code is valid`() {
        val codes = "0123456789abcdefklmnor"
        for (c in codes) {
            assertTrue(ConfigValidator.hasValidColorCodes("&${c}text"), "expected '&$c' to be valid")
        }
    }

    @Test
    fun `legacy codes are valid uppercase too`() {
        assertTrue(ConfigValidator.hasValidColorCodes("&ARed and &LBold"))
    }

    @Test
    fun `a well-formed hex code is valid`() {
        assertTrue(ConfigValidator.hasValidColorCodes("&#00AEEFGradient start"))
    }

    @Test
    fun `an ampersand not followed by a recognised code is invalid`() {
        assertFalse(ConfigValidator.hasValidColorCodes("Tom & Jerry"))
    }

    @Test
    fun `a truncated hex code is invalid`() {
        assertFalse(ConfigValidator.hasValidColorCodes("&#00AEtext"))
    }

    @Test
    fun `multiple valid codes in one string are all accepted`() {
        assertTrue(ConfigValidator.hasValidColorCodes("&aGreen &#FF0000Red &lBold"))
    }

    // ---- isRecognizedOperator ----

    @Test
    fun `standard comparison symbols are recognised`() {
        for (op in listOf("==", "!=", ">", "<", ">=", "<=")) {
            assertTrue(ConfigValidator.isRecognizedOperator(op), "expected '$op' to be recognised")
        }
    }

    @Test
    fun `word-form operator aliases are recognised case-insensitively`() {
        assertTrue(ConfigValidator.isRecognizedOperator("Equals"))
        assertTrue(ConfigValidator.isRecognizedOperator("CONTAINS"))
        assertTrue(ConfigValidator.isRecognizedOperator("  starts_with  "))
    }

    @Test
    fun `a typo like triple-equals is not recognised`() {
        assertFalse(ConfigValidator.isRecognizedOperator("==="))
    }

    // ---- isRecognizedAnimationPreset ----

    @Test
    fun `every documented preset name is recognised`() {
        for (preset in listOf("static", "fade", "color", "gradient", "typing", "scrolling", "pulse", "wave")) {
            assertTrue(ConfigValidator.isRecognizedAnimationPreset(preset))
            assertTrue(ConfigValidator.isRecognizedAnimationPreset(preset.uppercase()))
        }
    }

    @Test
    fun `an unrecognised preset name is flagged`() {
        assertFalse(ConfigValidator.isRecognizedAnimationPreset("fadein"))
    }

    // ---- scanSection: recursive structural scan against real YamlConfiguration ----

    @Test
    fun `a clean config produces no issues`() {
        val yaml = load(
            """
            title: "&6Clean Board"
            conditions:
              world: [world]
              placeholder: "%rl_online%"
              operator: ">="
              value: "1"
            lines:
              - "&7Always shown"
              - text: "&aConditional"
                animation:
                  enabled: true
                  preset: gradient
            """.trimIndent()
        )
        val issues = ConfigValidator.scanSection("clean.yml", yaml)
        assertTrue(issues.isEmpty(), "expected no issues, got: $issues")
    }

    @Test
    fun `an unrecognised top-level operator is flagged with its path`() {
        val yaml = load(
            """
            conditions:
              placeholder: "%rl_online%"
              operator: "==="
              value: "1"
            """.trimIndent()
        )
        val issues = ConfigValidator.scanSection("bad-operator.yml", yaml)
        assertEquals(1, issues.size)
        assertEquals("conditions.operator", issues.first().path)
        assertTrue(issues.first().problem.contains("==="))
    }

    @Test
    fun `an invalid color code in a line is flagged with its list index`() {
        val yaml = load(
            """
            lines:
              - "Tom & Jerry"
            """.trimIndent()
        )
        val issues = ConfigValidator.scanSection("bad-color.yml", yaml)
        assertEquals(1, issues.size)
        assertEquals("lines[0]", issues.first().path)
    }

    @Test
    fun `an unrecognised preset nested inside a line's animation block is still caught`() {
        // This is exactly the "recurse into nested maps at any depth" fix from this phase -
        // previously only conditions.placeholders[i].operator/.value were checked inside a
        // list item; a line's own nested animation map was not recursed into at all.
        val yaml = load(
            """
            lines:
              - text: "&aHello"
                animation:
                  enabled: true
                  preset: fadein
            """.trimIndent()
        )
        val issues = ConfigValidator.scanSection("bad-preset.yml", yaml)
        assertEquals(1, issues.size)
        assertEquals("lines[0].animation.preset", issues.first().path)
        assertTrue(issues.first().problem.contains("fadein"))
    }

    @Test
    fun `an unrecognised operator inside a conditions placeholders list item is caught`() {
        val yaml = load(
            """
            conditions:
              placeholders:
                - placeholder: "%rl_online%"
                  operator: "??"
                  value: "1"
            """.trimIndent()
        )
        val issues = ConfigValidator.scanSection("bad-list-operator.yml", yaml)
        assertEquals(1, issues.size)
        assertEquals("conditions.placeholders[0].operator", issues.first().path)
    }

    private fun load(yamlText: String): YamlConfiguration = YamlConfiguration.loadConfiguration(StringReader(yamlText))
}
