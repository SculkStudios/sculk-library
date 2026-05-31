package studio.sculk.effects

import org.bukkit.Location
import studio.sculk.annotation.SculkStable
import studio.sculk.series.SculkSeries

/** Config-friendly particle effect description. */
@SculkStable
public data class ParticleEffectDescriptor(
    public val particle: String,
    public val count: Int = 1,
    public val offsetX: Double = 0.0,
    public val offsetY: Double = 0.0,
    public val offsetZ: Double = 0.0,
    public val speed: Double = 0.0,
) {
    /** Spawns this effect at [location]. Unknown particles fail with a clear exception. */
    public fun spawn(location: Location) {
        particle(SculkSeries.requireParticle(particle)) {
            this.location = location
            count = this@ParticleEffectDescriptor.count
            offset(offsetX, offsetY, offsetZ)
            speed = this@ParticleEffectDescriptor.speed
            spawn()
        }
    }
}

/** Config-friendly sound effect description. */
@SculkStable
public data class SoundEffectDescriptor(
    public val sound: String,
    public val volume: Float = 1.0f,
    public val pitch: Float = 1.0f,
) {
    /** Plays this sound at [location]. Unknown sounds fail with a clear exception. */
    public fun playAt(location: Location) {
        sound(SculkSeries.requireSound(sound)) {
            volume = this@SoundEffectDescriptor.volume
            pitch = this@SoundEffectDescriptor.pitch
            playAt(location)
        }
    }
}
