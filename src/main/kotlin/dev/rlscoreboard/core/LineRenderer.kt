package dev.rlscoreboard.core

import dev.rlscoreboard.animation.AnimationEngine
import dev.rlscoreboard.api.model.AnimatedText
import dev.rlscoreboard.condition.ConditionEngine
import dev.rlscoreboard.condition.ConditionSet
import dev.rlscoreboard.placeholder.PlaceholderEngine
import org.bukkit.entity.Player

/** Resolves one animated, condition-gated piece of text (a title or a line) down to plain text for a player. */
class LineRenderer(
    private val placeholders: PlaceholderEngine,
    private val conditions: ConditionEngine,
    private val animations: AnimationEngine
) {

    /** Returns null when [conditionSet] doesn't pass - callers should skip the line entirely rather than render it blank. */
    fun render(animatedText: AnimatedText, conditionSet: ConditionSet, player: Player): String? {
        if (!conditions.evaluate(conditionSet, player)) return null
        val frame = animations.currentFrame(animatedText)
        return placeholders.resolve(frame, player)
    }
}
