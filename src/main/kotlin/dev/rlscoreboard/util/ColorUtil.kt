package dev.rlscoreboard.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.logging.Logger
import java.util.regex.Pattern

/**
 * Converts the '&' legacy colour codes, '&#RRGGBB' hex codes, and (as of the public-release-
 * upgrade pass) a restricted MiniMessage tag subset used throughout RLScoreboard's YAML
 * configs into Adventure [Component]s, since modern Paper's scoreboard/team APIs are
 * Component-based rather than raw-String-based (design spec section 15).
 *
 * **Which path a string takes**: any raw string containing a `<tag>`-shaped substring (see
 * [MINIMESSAGE_TAG_HINT]) is parsed as MiniMessage *in full* - legacy `&` codes elsewhere in
 * that same string are not additionally interpreted, so a line using `<gradient:...>` should
 * use MiniMessage's own `<red>`/`<#RRGGBB>` for any other colors in that line, not `&c`. A
 * string with no `<...>` shape at all (the overwhelming majority of existing configs) takes
 * the exact same legacy/hex path as every previous version - zero behavior change unless a
 * config opts in by actually writing a tag.
 *
 * **Why only a restricted tag subset, not [StandardTags.all]** (section 28: config data is
 * never trusted): by the time a string reaches this function, placeholders have usually
 * already been substituted into it - which can include player-influenced text (a nickname) or
 * a third-party plugin's placeholder output, neither of which this plugin controls. An
 * unrestricted MiniMessage parser would treat a substituted `<click:run_command:...>` or
 * `<hover:...>` appearing in such text as a *real* interactive component. [SAFE_TAGS] only
 * enables the purely visual tags (color, gradient, rainbow, decorations, reset, newline) -
 * every other tag (click, hover, insert, key, nbt, score, selector, translatable, font) is
 * simply not in the resolver, which Adventure's own docs confirm means it's "interpreted as
 * literal text" rather than executed - so the worst case from untrusted substituted content
 * is a cosmetically odd-looking line, never an unintended clickable/hoverable/command
 * component.
 */
object ColorUtil {

    private val HEX_PATTERN: Pattern = Pattern.compile("&#([A-Fa-f0-9]{6})")
    private val SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexColors()
        .build()

    private val SAFE_TAGS: TagResolver = TagResolver.builder()
        .resolver(StandardTags.color())
        .resolver(StandardTags.gradient())
        .resolver(StandardTags.rainbow())
        .resolver(StandardTags.decorations())
        .resolver(StandardTags.reset())
        .resolver(StandardTags.newline())
        .build()

    private val MINI_MESSAGE: MiniMessage = MiniMessage.builder().tags(SAFE_TAGS).build()

    /** A bare `<word>` or `<word:args>` shaped substring - purely a "should we even attempt MiniMessage" hint, not a validity check. */
    private val MINIMESSAGE_TAG_HINT = Regex("""<[a-zA-Z][\w:#,]*>""")

    /** Set once by [dev.rlscoreboard.RLScoreboardPlugin] at startup so a MiniMessage parse failure can be logged with the plugin's own logger. Null just means "fail silently, fall back" - never a crash either way (section 15: "Không để parser lỗi làm crash scoreboard... log warning, fallback text"). */
    var logger: Logger? = null

    fun toComponent(raw: String): Component {
        if (!MINIMESSAGE_TAG_HINT.containsMatchIn(raw)) return toComponentLegacy(raw)
        return runCatching { MINI_MESSAGE.deserialize(raw) }
            .getOrElse { error ->
                logger?.warning("Invalid MiniMessage in \"$raw\" (${error.message}) - falling back to legacy color parsing for this line.")
                toComponentLegacy(raw)
            }
    }

    private fun toComponentLegacy(raw: String): Component = SERIALIZER.deserialize(toSectionLegacy(raw))

    /** Converts to a plain '§'-coded legacy string, for the rare spot that still needs one. Always legacy/hex only, never MiniMessage - some callers genuinely need a raw string, not a [Component]. */
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
