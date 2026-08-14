package dev.rlscoreboard.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.regex.Pattern

/**
 * Converts the '&' legacy colour codes and '&#RRGGBB' hex codes used throughout
 * RLScoreboard's YAML configs into Adventure [Component]s, since modern Paper's
 * scoreboard/team APIs are Component-based rather than raw-String-based.
 */
object ColorUtil {

    private val HEX_PATTERN: Pattern = Pattern.compile("&#([A-Fa-f0-9]{6})")
    private val SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexColors()
        .build()

    fun toComponent(raw: String): Component = SERIALIZER.deserialize(toSectionLegacy(raw))

    /** Converts to a plain '§'-coded legacy string, for the rare spot that still needs one. */
    fun toSectionLegacy(raw: String): String = translateHex(raw).replace('&', '§')

    // "&#RRGGBB" -> "&x&R&R&G&G&B&B" so the later blanket '&' -> '§' pass turns it into the
    // "§x§R§R§G§G§B§B" form LegacyComponentSerializer's hexColors() understands.
    private fun translateHex(raw: String): String {
        val matcher = HEX_PATTERN.matcher(raw)
        if (!matcher.find()) return raw
        matcher.reset()
        val builder = StringBuilder()
        var last = 0
        while (matcher.find()) {
            builder.append(raw, last, matcher.start())
            builder.append("&x")
            for (c in matcher.group(1)) builder.append('&').append(c)
            last = matcher.end()
        }
        builder.append(raw, last, raw.length)
        return builder.toString()
    }
}
