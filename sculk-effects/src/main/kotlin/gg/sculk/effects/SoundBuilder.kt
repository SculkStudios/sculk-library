package gg.sculk.effects

import gg.sculk.core.annotation.SculkStable
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * Builds and plays a sound effect.
 *
 * Example:
 * ```kotlin
 * sound(Sound.ENTITY_PLAYER_LEVELUP) {
 *     volume = 1.0f
 *     pitch  = 1.2f
 *     playTo(player)
 * }
 * ```
 */
@SculkStable
public class SoundBuilder(
    public val type: Sound,
) {
    /** Playback volume. Defaults to 1.0. */
    public var volume: Float = 1.0f

    /** Playback pitch. Defaults to 1.0. */
    public var pitch: Float = 1.0f

    /**
     * Plays the sound to [player] at their current location.
     */
    @SculkStable
    public fun playTo(player: Player) {
        player.playSound(player.location, type, volume, pitch)
    }

    /**
     * Plays the sound at [location] for all nearby players in range.
     */
    @SculkStable
    public fun playAt(location: Location) {
        val world = requireNotNull(location.world) { "Location world must not be null" }
        world.playSound(location, type, volume, pitch)
    }
}

/**
 * Creates a [SoundBuilder] for [type] and applies [block].
 *
 * Call [SoundBuilder.playTo] or [SoundBuilder.playAt] inside the block to fire
 * immediately, or hold the builder for use in a timeline step.
 */
@SculkStable
public fun sound(
    type: Sound,
    block: SoundBuilder.() -> Unit,
): SoundBuilder = SoundBuilder(type).apply(block)
