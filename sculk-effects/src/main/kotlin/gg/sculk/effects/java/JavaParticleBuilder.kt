package gg.sculk.effects.java

import gg.sculk.core.annotation.SculkStable
import gg.sculk.effects.ParticleBuilder
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player

/**
 * Fluent Java builder for particle effects.
 *
 * ```java
 * JavaParticleBuilder.of(Particle.FLAME)
 *     .location(player.getLocation())
 *     .count(20)
 *     .offset(0.5, 0.5, 0.5)
 *     .speed(0.1)
 *     .spawn();
 *
 * // Restrict to specific players:
 * JavaParticleBuilder.of(Particle.HEART)
 *     .location(player.getLocation())
 *     .count(5)
 *     .receivers(player)
 *     .spawn();
 * ```
 */
@SculkStable
public class JavaParticleBuilder private constructor(
    private val builder: ParticleBuilder,
) {
    public companion object {
        /**
         * Creates a new [JavaParticleBuilder] for the given [type].
         */
        @SculkStable
        @JvmStatic
        public fun of(type: Particle): JavaParticleBuilder = JavaParticleBuilder(ParticleBuilder(type))
    }

    /** Sets the location to spawn the particle at. */
    @SculkStable
    public fun location(loc: Location): JavaParticleBuilder {
        builder.location = loc
        return this
    }

    /** Sets the number of particles to spawn. */
    @SculkStable
    public fun count(count: Int): JavaParticleBuilder {
        builder.count = count
        return this
    }

    /** Sets the XYZ spread offsets. */
    @SculkStable
    public fun offset(
        x: Double,
        y: Double,
        z: Double,
    ): JavaParticleBuilder {
        builder.offset(x, y, z)
        return this
    }

    /** Sets the particle speed / extra data parameter. */
    @SculkStable
    public fun speed(speed: Double): JavaParticleBuilder {
        builder.speed = speed
        return this
    }

    /**
     * Restricts particle visibility to [players] only.
     *
     * When set, particles are sent directly to each receiver rather than
     * being broadcast to the world.
     */
    @SculkStable
    public fun receivers(vararg players: Player): JavaParticleBuilder {
        builder.receivers(*players)
        return this
    }

    /**
     * Spawns the particle effect.
     *
     * @throws IllegalStateException if [location] was not set.
     */
    @SculkStable
    public fun spawn(): JavaParticleBuilder {
        builder.spawn()
        return this
    }
}
