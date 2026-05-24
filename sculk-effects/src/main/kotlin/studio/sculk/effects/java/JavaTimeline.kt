package studio.sculk.effects.java

import studio.sculk.core.SculkHandle
import studio.sculk.core.annotation.SculkStable
import studio.sculk.core.scheduler.SculkScheduler
import studio.sculk.effects.TimelineBuilder

/**
 * Fluent Java builder for [studio.sculk.effects.AnimationTimeline].
 *
 * Steps are keyed by absolute tick offset from when [start] is called.
 *
 * ```java
 * SculkHandle handle = JavaTimeline.builder()
 *     .at(0,  () -> JavaParticleBuilder.of(Particle.FLAME)
 *                       .location(origin).count(10).spawn())
 *     .at(10, () -> JavaSoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).playTo(player))
 *     .at(20, () -> JavaParticleBuilder.of(Particle.END_ROD)
 *                       .location(origin).count(30).spawn())
 *     .loop(3)
 *     .start(scheduler);
 *
 * // Cancel early:
 * handle.close();
 * ```
 */
@SculkStable
public class JavaTimeline private constructor() {
    private val builder = TimelineBuilder()

    public companion object {
        /**
         * Creates a new [JavaTimeline] builder.
         */
        @SculkStable
        @JvmStatic
        public fun builder(): JavaTimeline = JavaTimeline()
    }

    /**
     * Registers an [action] to execute at [tick] ticks after the timeline starts.
     */
    @SculkStable
    public fun at(
        tick: Int,
        action: Runnable,
    ): JavaTimeline {
        builder.at(tick) { action.run() }
        return this
    }

    /**
     * Sets how many times the timeline loops. Defaults to 1 (play once).
     */
    @SculkStable
    public fun loop(times: Int): JavaTimeline {
        builder.loop(times)
        return this
    }

    /**
     * Starts the timeline using [scheduler].
     *
     * Returns a [SculkHandle] that cancels all remaining scheduled steps when closed.
     */
    @SculkStable
    public fun start(scheduler: SculkScheduler): SculkHandle = builder.build().start(scheduler)
}
