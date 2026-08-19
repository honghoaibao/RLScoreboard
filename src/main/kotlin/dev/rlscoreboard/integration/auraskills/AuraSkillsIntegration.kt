package dev.rlscoreboard.integration.auraskills

import dev.aurelium.auraskills.api.AuraSkillsApi
import dev.aurelium.auraskills.api.skill.Skills
import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.PlaceholderProvider
import dev.rlscoreboard.integration.AbstractIntegration
import dev.rlscoreboard.leaderboard.datasource.AuraSkillsPowerLevelDataSource

/**
 * Registers the native "auraskills_powerlevel" leaderboard datasource
 * ([AuraSkillsPowerLevelDataSource]) plus, as of the public-release-upgrade pass, per-skill
 * level/XP placeholders (design spec section 9).
 *
 * `"power"` and `"skills"` are isolated in their own `runCatching` blocks (see
 * [dev.rlscoreboard.integration.Integration.enable]'s KDoc for why) - a future AuraSkills API
 * change breaking one doesn't have to take the other down with it.
 */
class AuraSkillsIntegration(private val plugin: RLScoreboardPlugin) : AbstractIntegration() {
    override val id = "auraskills"
    override val pluginName = "AuraSkills"
    override val minSupportedVersion = "2.0"
    override val maxTestedVersion = "2.3"
    override val capabilities = setOf("power", "skills")

    override fun enable(): Set<String> {
        val active = mutableSetOf<String>()

        runCatching { plugin.leaderboardEngine.dataSources.register(AuraSkillsPowerLevelDataSource()) }
            .onSuccess { active += "power" }

        runCatching { registerSkillPlaceholders() }
            .onSuccess { active += "skills" }

        return active
    }

    /**
     * Registers `%rl_skill_level_<skill>%` and `%rl_skill_xp_<skill>%` for every *enabled*
     * default AuraSkills skill, looping over [Skills.values] rather than one hand-written
     * block per skill - if AuraSkills adds or removes a default skill in a future version,
     * this adapts with no RLScoreboard code change. `<skill>` is the skill's own lowercase
     * name (the `Skill` interface's `name()` method - called from Kotlin as the property
     * `.name`, not `.name()`; see the inline comment at the call site for why). Method
     * signatures (`SkillsUser.getSkillLevel(Skill)`, `getSkillXp(Skill)`, `Skill.isEnabled()`,
     * `Skill.name()`) confirmed against the official 2.3.3 `api-bukkit` javadoc before writing
     * this, not guessed - see docs/INTEGRATIONS.md for the citation.
     *
     * **Deliberately not implemented**: a "Skill Progress" percentage placeholder from the
     * original wishlist. `getSkillXp(Skill)` returns "the amount ranges from 0 to the XP
     * required to progress to the next skill level" per its own javadoc, but the
     * *denominator* - the actual XP-required-for-next-level value - was not found exposed
     * anywhere on the public `api`/`api-bukkit` module (only an internal, non-API
     * `XpRequirements` class turned up, in the old pre-rename `com.archyx.aureliumskills`
     * package, unreachable from a `compileOnly` dependency on the public API artifact).
     * Rather than call a plausible-looking method name that was never actually confirmed to
     * exist and would simply fail to compile, `%rl_skill_xp_<skill>%` exposes the raw
     * XP-in-level number only - see ROADMAP.md for this gap tracked honestly, not silently
     * skipped.
     *
     * **Also not covered**: custom skills registered by *other* plugins via AuraSkills'
     * `NamespacedId`-based custom-skill system - only [Skills], the built-in default set, is
     * enumerated here.
     */
    private fun registerSkillPlaceholders() {
        val api = AuraSkillsApi.get() ?: return

        for (skill in Skills.values()) {
            if (!runCatching { skill.isEnabled() }.getOrDefault(false)) continue
            // Skills.name() (a java.lang.Enum method the Skill interface also declares) is
            // exposed to Kotlin as the property `.name`, not a callable `.name()` - Kotlin
            // gives every java.lang.Enum subtype that special property mapping, which takes
            // priority over calling it as a function even though the Skill interface declares
            // it as one. Writing `.name()` compiles as "invoke the String value returned by
            // .name as a function", which fails with a confusing, unrelated
            // "DeepRecursiveFunction" error - caught by the first real GitHub Actions build,
            // not by anything checkable without a real Kotlin compiler.
            val key = skill.name.lowercase()

            plugin.placeholderEngine.register("rl_skill_level_$key", PlaceholderProvider { player ->
                player?.let { p -> runCatching { api.getUser(p.uniqueId).getSkillLevel(skill) }.getOrNull()?.toString() } ?: ""
            })

            plugin.placeholderEngine.register("rl_skill_xp_$key", PlaceholderProvider { player ->
                player?.let { p ->
                    runCatching { api.getUser(p.uniqueId).getSkillXp(skill) }.getOrNull()?.let { "%.0f".format(it) }
                } ?: ""
            })
        }
    }
}
