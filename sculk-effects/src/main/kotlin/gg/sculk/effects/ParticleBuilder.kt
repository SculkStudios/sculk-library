package gg.sculk.effects

import gg.sculk.core.annotation.SculkStable
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * Builds and spawns a particle effect at a given [location].
 *
 * By default, particles are visible to all players in range. Use [receivers] to
 * restrict visibility to specific players.
 *
 * Example:
 * ```kotlin
 * particle(Particle.FLAME) {
 *     location = player.location
 *     count = 20
 *     offset(0.5, 0.5, 0.5)
 *     speed = 0.1
 *     spawn()
 * }
 *
 * // Only the player who triggered the effect sees it:
 * particle(Particle.HEART) {
 *     location = player.location
 *     count = 5
 *     receivers(player)
 *     spawn()
 * }
 * ```
 */
@SculkStable
public class ParticleBuilder(
    public val type: Particle,
) {
    /** Location to spawn the particle at. Must be set before calling [spawn]. */
    public var location: Location? = null

    /** Number of particles to spawn. Defaults to 1. */
    public var count: Int = 1

    /** X spread offset. */
    public var offsetX: Double = 0.0

    /** Y spread offset. */
    public var offsetY: Double = 0.0

    /** Z spread offset. */
    public var offsetZ: Double = 0.0

    /** Particle speed / extra data parameter. */
    public var speed: Double = 0.0

    private var receiverList: List<Player>? = null

    /** Sets all three offsets at once. */
    @SculkStable
    public fun offset(
        x: Double,
        y: Double,
        z: Double,
    ) {
        offsetX = x
        offsetY = y
        offsetZ = z
    }

    /**
     * Restricts particle visibility to [players] only.
     *
     * When set, [spawn] calls `player.spawnParticle` for each receiver instead of
     * broadcasting to the world. This is useful for per-player effects like visual
     * feedback or aura effects that shouldn't disturb other players.
     *
     * ```kotlin
     * particle(Particle.CRIT) {
     *     location = attacker.location
     *     count = 10
     *     receivers(attacker, victim)
     *     spawn()
     * }
     * ```
     */
    @SculkStable
    public fun receivers(vararg players: Player) {
        receiverList = players.toList()
    }

    /**
     * Spawns the particle effect.
     *
     * If [receivers] was called, each receiver gets the particle individually.
     * Otherwise the effect is broadcast to all players in range.
     *
     * @throws IllegalStateException if [location] has not been set.
     */
    @SculkStable
    public fun spawn() {
        val loc = requireNotNull(location) { "ParticleBuilder.location must be set before calling spawn()" }
        val recvs = receiverList
        if (recvs != null) {
            recvs.forEach { player ->
                player.spawnParticle(type, loc, count, offsetX, offsetY, offsetZ, speed)
            }
        } else {
            val world: World = requireNotNull(loc.world) { "Location world must not be null" }
            world.spawnParticle(type, loc, count, offsetX, offsetY, offsetZ, speed)
        }
    }
}

/**
 * Creates a [ParticleBuilder] for [type], applies [block], and returns the builder.
 *
 * Call [ParticleBuilder.spawn] inside the block to fire immediately, or hold the
 * builder and call it later (e.g. inside a timeline step).
 */
@SculkStable
public fun particle(
    type: Particle,
    block: ParticleBuilder.() -> Unit,
): ParticleBuilder = ParticleBuilder(type).apply(block)
