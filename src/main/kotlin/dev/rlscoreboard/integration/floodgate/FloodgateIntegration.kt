package dev.rlscoreboard.integration.floodgate

import dev.rlscoreboard.integration.AbstractIntegration
import java.util.UUID

/**
 * Detection-only - no `floodgate-api` compile-time dependency is added for this, so
 * [isLikelyBedrockPlayer] below is a **heuristic**, not a call into Floodgate's real
 * `FloodgateApi.isFloodgatePlayer(UUID)`. Floodgate assigns Bedrock players a UUID with its
 * version nibble forced to `0` (a well-documented Floodgate convention, since Bedrock
 * accounts have no real Mojang UUID to use) - checking that nibble is a legitimate,
 * dependency-free way to guess "is this player connected via Floodgate", but it is only ever
 * a guess: it can't distinguish "this is a Floodgate/Bedrock player" from "some other system
 * generated a version-0 UUID for an unrelated reason". Anything permission- or
 * economy-sensitive should not be gated on this alone.
 *
 * The honest, verified path to a real per-player check is adding `org.geysermc.floodgate:api`
 * as a `compileOnly` dependency and calling `FloodgateApi.getInstance().isFloodgatePlayer(uuid)`
 * directly - that's a documented future upgrade for this integration, deferred here because
 * it needs a live Floodgate install to verify against and this environment has no network
 * access to pull the dependency or test it.
 */
class FloodgateIntegration : AbstractIntegration() {
    override val id = "floodgate"
    override val pluginName = "Floodgate"
    override val pluginId = "floodgate"
    override val minSupportedVersion = "2.0"
    override val maxTestedVersion = "2.2"
    override val capabilities = setOf("bedrock_identity")
    override fun enable() {}

    companion object {
        /**
         * Heuristic only - see class KDoc. UUID version is the 13th hex digit (index 14 in
         * the dashed string); Floodgate forces it to `0`, which is not a valid RFC 4122
         * version nibble for a real Mojang-issued UUID, so seeing it is a reasonably strong
         * (but not certain) signal.
         */
        fun isLikelyBedrockPlayer(uuid: UUID): Boolean = uuid.version() == 0
    }
}
