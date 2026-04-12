package gg.sculk.effects

import gg.sculk.core.annotation.SculkStable
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * Builds and plays a sound effect.
 *
 * Create with [SoundBuilder.of] for a [Sound] enum or [SoundBuilder.ofKey] for a
 * namespaced string key (e.g. `"entity.player.levelup"`, `"myplugin:ui.click"`).
 *
 * Convenience DSL functions are provided for both forms:
 * ```kotlin
 * // Enum form:
 * sound(Sound.ENTITY_PLAYER_LEVELUP) {
 *     volume = 1.0f
 *     pitch  = 1.2f
 *     playTo(player)
 * }
 *
 * // String-key form (custom resource pack sounds, namespaced keys):
 * sound("myplugin:ui.click") {
 *     volume = 0.8f
 *     playTo(player)
 * }
 * ```
 */
@SculkStable
public class SoundBuilder private constructor(
    private val soundType: Sound?,
    private val soundKey: String?,
) {
    /** Playback volume. Defaults to 1.0. */
    public var volume: Float = 1.0f

    /** Playback pitch. Defaults to 1.0. */
    public var pitch: Float = 1.0f

    public companion object {
        /**
         * Creates a [SoundBuilder] from a [Sound] enum.
         *
         * ```kotlin
         * val builder = SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP)
         * ```
         */
        @SculkStable
        @JvmStatic
        public fun of(type: Sound): SoundBuilder = SoundBuilder(type, null)

        /**
         * Creates a [SoundBuilder] from a namespaced string key.
         *
         * Accepts Minecraft namespaced keys (`"entity.player.levelup"`) or
         * custom resource-pack sounds (`"myplugin:ui.click"`).
         *
         * ```kotlin
         * val builder = SoundBuilder.ofKey("myplugin:ui.click")
         * ```
         */
        @SculkStable
        @JvmStatic
        public fun ofKey(key: String): SoundBuilder = SoundBuilder(null, key)
    }

    /**
     * Plays the sound to [player] at their current location.
     */
    @SculkStable
    public fun playTo(player: Player) {
        if (soundType != null) {
            player.playSound(player.location, soundType, volume, pitch)
        } else {
            player.playSound(player.location, soundKey!!, volume, pitch)
        }
    }

    /**
     * Plays the sound at [location] for all nearby players in range.
     */
    @SculkStable
    public fun playAt(location: Location) {
        val world = requireNotNull(location.world) { "Location world must not be null" }
        if (soundType != null) {
            world.playSound(location, soundType, volume, pitch)
        } else {
            world.playSound(location, soundKey!!, volume, pitch)
        }
    }
}

/**
 * Creates a [SoundBuilder] for the given [Sound] enum and applies [block].
 *
 * Call [SoundBuilder.playTo] or [SoundBuilder.playAt] inside the block to fire
 * immediately, or hold the builder for use in a timeline step.
 */
@SculkStable
public fun sound(
    type: Sound,
    block: SoundBuilder.() -> Unit,
): SoundBuilder = SoundBuilder.of(type).apply(block)

/**
 * Creates a [SoundBuilder] from a namespaced string [key] and applies [block].
 *
 * Use this overload for custom resource-pack sounds or when you have the key as a string.
 *
 * ```kotlin
 * sound("myplugin:ui.click") {
 *     volume = 0.8f
 *     playTo(player)
 * }
 * ```
 */
@SculkStable
public fun sound(
    key: String,
    block: SoundBuilder.() -> Unit,
): SoundBuilder = SoundBuilder.ofKey(key).apply(block)
