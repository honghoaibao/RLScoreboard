package dev.rlscoreboard.config

/**
 * Implements the checks `/rlscoreboard validate-language <locale>` reports (design spec
 * section L): missing keys, unknown keys, placeholder-token mismatches against the English
 * reference, and invalid color codes. Pure string/set logic against [LocaleManager]'s already
 * -loaded caches - no Bukkit dependency, so it's independently testable and reusable by any
 * future CI/lint step.
 */
class LocaleValidator(private val localeManager: LocaleManager) {

    data class Result(
        val locale: String,
        val referenceKeyCount: Int,
        val missingKeys: List<String>,
        val unknownKeys: List<String>,
        val brokenPlaceholderKeys: List<String>,
        val invalidColorKeys: List<String>
    ) {
        val isClean: Boolean
            get() = missingKeys.isEmpty() && unknownKeys.isEmpty() && brokenPlaceholderKeys.isEmpty() && invalidColorKeys.isEmpty()
    }

    companion object {
        private val PLACEHOLDER_TOKEN = Regex("""\{[a-zA-Z0-9_]+\}""")

        /** A legacy '&' color/format code (0-9/a-f/k-o/r, either case) or a 6-digit '&#RRGGBB' hex code - the only two forms `ColorUtil` treats as valid. */
        private val VALID_AMPERSAND_CODE = Regex("""&(#[0-9a-fA-F]{6}|[0-9a-fA-Fk-oK-OrR])""")
    }

    /** Validates [locale] against the English reference set - see [LocaleManager.referenceKeys]. */
    fun validate(locale: String): Result {
        val normalized = locale.trim().lowercase()
        val referenceKeys = localeManager.referenceKeys()
        val localeKeys = localeManager.keysOf(normalized)

        val missing = (referenceKeys - localeKeys).sorted()
        val unknown = (localeKeys - referenceKeys).sorted()

        val brokenPlaceholders = referenceKeys.intersect(localeKeys).filter { key ->
            val referenceTokens = tokensOf(localeManager.rawValue(LocaleManager.DEFAULT_LOCALE, key))
            val localeTokens = tokensOf(localeManager.rawValue(normalized, key))
            referenceTokens != localeTokens
        }.sorted()

        val invalidColors = localeKeys.filter { key ->
            hasInvalidColorCode(localeManager.rawValue(normalized, key) ?: "")
        }.sorted()

        return Result(normalized, referenceKeys.size, missing, unknown, brokenPlaceholders, invalidColors)
    }

    private fun tokensOf(text: String?): Set<String> =
        PLACEHOLDER_TOKEN.findAll(text ?: "").map { it.value }.toSet()

    private fun hasInvalidColorCode(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            if (text[i] == '&') {
                val match = VALID_AMPERSAND_CODE.matchAt(text, i) ?: return true
                i += match.value.length
            } else {
                i++
            }
        }
        return false
    }
}
