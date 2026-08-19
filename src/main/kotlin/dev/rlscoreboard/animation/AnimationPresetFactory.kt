package dev.rlscoreboard.animation

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Procedurally generates the `frames` list for each named animation preset (design spec
 * section 14: static/fade/color/gradient/typing/scrolling/pulse/wave) from a base text plus a
 * handful of parameters, so an admin writes `preset: fade` in config instead of hand-authoring
 * every individual frame. The existing frame-cycling [AnimationEngine] plays back whatever
 * this produces completely unchanged - generation happens once, at config-load time (see
 * [dev.rlscoreboard.config.BoardConfigLoader]), not per-tick, so this stays "animation phải
 * nhẹ" (section 14) by construction: the runtime cost of a procedurally-generated preset is
 * identical to a hand-written `frames:` list of the same length.
 *
 * `gradient` and `wave` generate real MiniMessage `<gradient:...>`/`<rainbow>` tags (see
 * [dev.rlscoreboard.util.ColorUtil]) rather than hand-rolled per-character color math -
 * Adventure's own gradient/rainbow renderer is better-tested than anything written here would
 * be, and this project already added (restricted, visual-tags-only) MiniMessage support this
 * same phase, so using it here is reusing verified work rather than duplicating it.
 */
object AnimationPresetFactory {

    private val DEFAULT_SOLID_COLORS = listOf("#FFFFFF", "#AAAAAA")
    private val DEFAULT_GRADIENT_COLORS = listOf("#00AEEF", "#7B61FF")

    /**
     * @param preset one of static/fade/color/gradient/typing/scrolling/pulse/wave (case-insensitive). An unrecognised name falls back to a single static frame rather than guessing.
     * @param text the base text (may already contain its own `&`/hex codes - typing/scrolling preserve them as-is; fade/color/pulse/gradient/wave override the whole line's color, so any existing codes in [text] are redundant for those presets, not a conflict).
     * @param colors hex colors (`#RRGGBB` or `RRGGBB`), used by fade/color/pulse/gradient/wave. Empty uses each preset's own sensible default.
     * @param frameCount how many frames to generate for the presets that animate continuously (fade/gradient/pulse/wave) - ignored by typing/scrolling, whose frame count is determined by the text/width themselves.
     * @param width visible window width for `scrolling` only.
     * @param speed characters advanced per frame for `scrolling` only.
     */
    fun generate(
        preset: String,
        text: String,
        colors: List<String> = emptyList(),
        frameCount: Int = 16,
        width: Int = 20,
        speed: Int = 1
    ): List<String> {
        if (text.isEmpty()) return listOf(text)
        val frames = frameCount.coerceIn(2, 64)

        return when (preset.trim().lowercase()) {
            "static" -> listOf(text)
            "fade" -> fade(text, colors.ifEmpty { DEFAULT_SOLID_COLORS }, frames)
            "color" -> colorCycle(text, colors.ifEmpty { DEFAULT_SOLID_COLORS })
            "gradient" -> shiftingGradient(text, colors.ifEmpty { DEFAULT_GRADIENT_COLORS }, frames, speed = 1)
            "typing" -> typing(text)
            "scrolling" -> scrolling(text, width.coerceAtLeast(4), speed.coerceAtLeast(1))
            "pulse" -> pulse(text, colors.ifEmpty { DEFAULT_SOLID_COLORS }, frames)
            "wave" -> if (colors.isEmpty()) rainbow(text, frames) else shiftingGradient(text, colors, frames, speed = 2)
            else -> listOf(text)
        }
    }

    /** Solid color fading from `colors.first()` to `colors.last()` across [frameCount] frames - a one-way fade-in, not a loop (matches how "fade" reads: something appearing, not oscillating - see [pulse] for the oscillating version). */
    private fun fade(text: String, colors: List<String>, frameCount: Int): List<String> {
        val from = parseHex(colors.first())
        val to = parseHex(colors.last())
        return (0 until frameCount).map { i ->
            val t = i.toDouble() / (frameCount - 1)
            solidColorFrame(text, lerp(from, to, t))
        }
    }

    /** One frame per configured color, cycling through the whole list in order. */
    private fun colorCycle(text: String, colors: List<String>): List<String> =
        colors.map { solidColorFrame(text, parseHex(it)) }

    /** Solid color oscillating back and forth between `colors.first()` and `colors.last()` (a triangle wave) - the "breathing" look "pulse" implies, as opposed to fade's one-way transition. */
    private fun pulse(text: String, colors: List<String>, frameCount: Int): List<String> {
        val from = parseHex(colors.first())
        val to = parseHex(colors.last())
        return (0 until frameCount).map { i ->
            val phase = i.toDouble() / frameCount
            val t = triangleWave(phase)
            solidColorFrame(text, lerp(from, to, t))
        }
    }

    /** A real MiniMessage `<gradient:...:phase>` across the whole line - `phase` is Adventure's own animation parameter for this tag (confirmed range -1..1), swept smoothly back and forth across [frameCount] frames via [triangleWave] so the loop has no jump-cut at the wrap point. [speed] scales how many back-and-forth sweeps happen over the full frame loop - `gradient` uses 1 (a single slow sweep), `wave` uses 2 (faster, more restless). */
    private fun shiftingGradient(text: String, colors: List<String>, frameCount: Int, speed: Int): List<String> {
        if (colors.size < 2) return listOf(solidColorFrame(text, parseHex(colors.firstOrNull() ?: "#FFFFFF")))
        val colorArgs = colors.joinToString(":") { "#${normalizeHex(it)}" }
        return (0 until frameCount).map { i ->
            val t = i.toDouble() / frameCount
            val phase = triangleWave(t * speed) * 2.0 - 1.0
            "<gradient:$colorArgs:${"%.2f".format(phase)}>$text</gradient>"
        }
    }

    /** Adventure's own built-in `<rainbow>` tag, phase-shifted per frame via MiniMessage's own rainbow phase argument. */
    private fun rainbow(text: String, frameCount: Int): List<String> =
        (0 until frameCount).map { i -> "<rainbow:${i}>$text</rainbow>" }

    /** Reveals [text] one character at a time - a typewriter effect. Preserves any `&`/hex codes already in [text] as-is (a substring never splits a color code in a way that breaks it worse than the admin's own text already could). */
    private fun typing(text: String): List<String> = (1..text.length).map { text.substring(0, it) }

    /** Classic horizontal marquee: pads [text] with a gap, then slides a [width]-character window across it, wrapping around, advancing [speed] characters per frame. Text already shorter than [width] just returns it unchanged - nothing to scroll. */
    private fun scrolling(text: String, width: Int, speed: Int): List<String> {
        if (text.length <= width) return listOf(text)
        val padded = "$text   "
        val doubled = padded + padded
        return (0 until padded.length step speed).map { offset -> doubled.substring(offset, offset + width) }
    }

    private fun solidColorFrame(text: String, rgb: Triple<Int, Int, Int>): String = "&#${toHexString(rgb)}$text"

    /** 0 at t=0, 1 at t=0.5, 0 at t=1 (and beyond, looping) - the back-and-forth shape [pulse] needs instead of a one-way [fade]. */
    private fun triangleWave(t: Double): Double {
        val phase = t - kotlin.math.floor(t)
        return 1.0 - abs(phase * 2.0 - 1.0)
    }

    private fun lerp(from: Triple<Int, Int, Int>, to: Triple<Int, Int, Int>, t: Double): Triple<Int, Int, Int> {
        val clamped = t.coerceIn(0.0, 1.0)
        fun mix(a: Int, b: Int) = (a + (b - a) * clamped).roundToInt().coerceIn(0, 255)
        return Triple(mix(from.first, to.first), mix(from.second, to.second), mix(from.third, to.third))
    }

    private fun normalizeHex(color: String): String = color.trim().removePrefix("#").uppercase()

    private fun parseHex(color: String): Triple<Int, Int, Int> {
        val hex = normalizeHex(color).padStart(6, '0').take(6)
        return runCatching {
            Triple(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
        }.getOrDefault(Triple(255, 255, 255))
    }

    private fun toHexString(rgb: Triple<Int, Int, Int>): String =
        "%02X%02X%02X".format(rgb.first, rgb.second, rgb.third)
}
