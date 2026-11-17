package studio.sculk.npc

import com.destroystokyo.paper.profile.PlayerProfile
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.fold
import java.util.Base64
import java.util.UUID

/** Which skin an NPC wears. */
@SculkExperimental
public sealed class Skin {
    /** Minecraft's default mannequin skin. */
    @SculkExperimental
    public data object Default : Skin()

    /**
     * The skin of the player with [uuid], looked up off every game thread once per `Npcs` and then
     * reused. The NPC wears the default skin until the lookup answers, and keeps it if the lookup
     * finds nothing or fails.
     */
    @SculkExperimental
    public class OfPlayer internal constructor(public val uuid: UUID) : Skin() {
        override fun equals(other: Any?): Boolean = other is OfPlayer && other.uuid == uuid

        override fun hashCode(): Int = uuid.hashCode()

        override fun toString(): String = "Skin.OfPlayer($uuid)"
    }

    /**
     * A `textures` profile property: Base64 [value] and, when Mojang signed it, its [signature].
     * Shown from the first tick, with no lookup. Some clients refuse unsigned textures, so pass the
     * signature whenever you have one.
     */
    @SculkExperimental
    public class Textures internal constructor(public val value: String, public val signature: String?) : Skin() {
        override fun equals(other: Any?): Boolean = other is Textures && other.value == value && other.signature == signature

        override fun hashCode(): Int = 31 * value.hashCode() + signature.hashCode()

        override fun toString(): String = "Skin.Textures(${value.take(PREVIEW)}…, signed=${signature != null})"
    }

    @SculkExperimental
    public companion object {
        private const val PREVIEW = 12
        private const val TEXTURES = "textures"

        /** The skin of the player with [uuid]; see [OfPlayer]. */
        public fun player(uuid: UUID): Skin = OfPlayer(uuid)

        /** Textures from code. Throws [IllegalArgumentException] for a value that is not a Base64 `textures` property. */
        public fun textures(value: String, signature: String? = null): Skin = when (val parsed = parse(value, signature)) {
            is SculkResult.Success -> parsed.value
            is SculkFailure -> throw IllegalArgumentException(parsed.describe())
        }

        /**
         * Textures from configuration or another untrusted source: [SculkFailure.InvalidInput] when
         * [value] is not Base64 of a `textures` property or [signature] is blank.
         */
        public fun parse(value: String, signature: String? = null): SculkResult<Skin> {
            val decoded = try {
                String(Base64.getDecoder().decode(value.trim()), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                return SculkFailure.InvalidInput("skin textures", "Base64 text", "text that does not decode")
            }
            if ("\"$TEXTURES\"" !in decoded) {
                return SculkFailure.InvalidInput("skin textures", "a Base64 textures property", "Base64 without a \"textures\" object")
            }
            if (signature != null && signature.isBlank()) {
                return SculkFailure.InvalidInput("skin signature", "Mojang's Base64 signature or none", "a blank signature")
            }
            return SculkResult.success(Textures(value.trim(), signature?.trim()))
        }

        /**
         * The skin [profile] carries: its textures when it has them, otherwise its player's skin by
         * UUID, otherwise [Default]. Copies what it needs, so later changes to [profile] do not reach the NPC.
         */
        public fun of(profile: PlayerProfile): Skin {
            val textures = profile.properties.firstOrNull { it.name == TEXTURES }
            if (textures != null) return parse(textures.value, textures.signature).fold({ it }, { profile.id?.let(::player) ?: Default })
            return profile.id?.let(::player) ?: Default
        }
    }
}

/** A resolved `textures` property, ready for the adapter. */
internal class SkinTexture(val value: String, val signature: String?)
