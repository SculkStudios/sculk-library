package gg.sculk.effects.java

import gg.sculk.core.annotation.SculkStable
import gg.sculk.effects.SoundBuilder
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * Fluent Java builder for sound effects.
 *
 * ```java
 * // Enum form:
 * JavaSoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP)
 *     .volume(1.0f)
 *     .pitch(1.2f)
 *     .playTo(player);
 *
 * // String-key form (custom resource pack sounds):
 * JavaSoundBuilder.ofKey("myplugin:ui.click")
 *     .volume(0.8f)
 *     .playTo(player);
 * ```
 */
@SculkStable
public class JavaSoundBuilder private constructor(
    private val builder: SoundBuilder,
) {
    public companion object {
        /**
         * Creates a [JavaSoundBuilder] from a [Sound] enum.
         */
        @SculkStable
        @JvmStatic
        public fun of(sound: Sound): JavaSoundBuilder = JavaSoundBuilder(SoundBuilder.of(sound))

        /**
         * Creates a [JavaSoundBuilder] from a namespaced string key.
         *
         * Accepts Minecraft keys (`"entity.player.levelup"`) or custom resource-pack
         * sounds (`"myplugin:ui.click"`).
         */
        @SculkStable
        @JvmStatic
        public fun ofKey(key: String): JavaSoundBuilder = JavaSoundBuilder(SoundBuilder.ofKey(key))
    }

    /** Sets the playback volume. */
    @SculkStable
    public fun volume(volume: Float): JavaSoundBuilder {
        builder.volume = volume
        return this
    }

    /** Sets the playback pitch. */
    @SculkStable
    public fun pitch(pitch: Float): JavaSoundBuilder {
        builder.pitch = pitch
        return this
    }

    /** Plays the sound to [player] at their current location. */
    @SculkStable
    public fun playTo(player: Player): JavaSoundBuilder {
        builder.playTo(player)
        return this
    }

    /** Plays the sound at [location] for all nearby players in range. */
    @SculkStable
    public fun playAt(location: Location): JavaSoundBuilder {
        builder.playAt(location)
        return this
    }
}
