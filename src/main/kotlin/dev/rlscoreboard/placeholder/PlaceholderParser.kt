package dev.rlscoreboard.placeholder

/** Low-level `%identifier%` tokenizer, shared by [PlaceholderEngine] and anything else that needs it. */
object PlaceholderParser {
    private val PATTERN = Regex("%([a-zA-Z0-9_]+)%")

    fun tokens(text: String): List<String> = PATTERN.findAll(text).map { it.groupValues[1] }.toList()

    fun replace(text: String, resolve: (String) -> String?): String {
        if (!text.contains('%')) return text
        return PATTERN.replace(text) { match -> resolve(match.groupValues[1]) ?: match.value }
    }
}
