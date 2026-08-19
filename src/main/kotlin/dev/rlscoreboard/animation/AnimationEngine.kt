package dev.rlscoreboard.animation

import dev.rlscoreboard.api.model.AnimatedText

/**
 * Computes the current animation frame for any animated text purely from elapsed time,
 * `frame = floor(now / interval) % frameCount`. Deliberately stateless: section 9 of the
 * design spec asks for a *central* scheduler rather than one task per animated line/player,
 * and since frame selection is a pure function of "now" there's nothing to schedule at all -
 * the single [dev.rlscoreboard.core.UpdateManager] heartbeat is enough no matter how many
 * boards/leaderboards are animated.
 */
class AnimationEngine(private val clock: () -> Long = { System.currentTimeMillis() }) {

    fun currentFrame(text: AnimatedText): String {
        if (!text.isAnimated) return text.frames.firstOrNull() ?: ""
        val intervalMillis = (text.intervalTicks * 50L).coerceAtLeast(50L)
        val elapsed = clock() / intervalMillis
        val index = if (text.loop) {
            (elapsed % text.frames.size).toInt()
        } else {
            elapsed.coerceAtMost((text.frames.size - 1).toLong()).toInt()
        }
        return text.frames[index]
    }
}
