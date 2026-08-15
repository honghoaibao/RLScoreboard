package dev.rlscoreboard.api.model

/**
 * A piece of text that may cycle through multiple frames over time (see section 9 of the
 * design spec - title/line animation). A single-frame [AnimatedText] is just static text;
 * [dev.rlscoreboard.animation.AnimationEngine] treats both cases uniformly.
 */
data class AnimatedText(
    val frames: List<String>,
    val intervalTicks: Long = 20L,
    val loop: Boolean = true
) {
    val isAnimated: Boolean get() = frames.size > 1

    companion object {
        fun static(text: String): AnimatedText = AnimatedText(listOf(text), 20L, true)
    }
}
