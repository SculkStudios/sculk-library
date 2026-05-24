package studio.sculk.effects.java

import studio.sculk.core.SculkHandle
import studio.sculk.core.annotation.SculkStable
import studio.sculk.core.scheduler.SculkScheduler
import studio.sculk.effects.SequenceBuilder

/**
 * Fluent Java builder for [studio.sculk.effects.AnimationSequence].
 *
 * Unlike [JavaTimeline] (absolute tick offsets), sequences use relative delays —
 * each [delay] call advances the cursor by that many ticks before the next step.
 *
 * ```java
 * SculkHandle handle = JavaSequence.builder()
 *     .step(() -> JavaParticleBuilder.of(Particle.FLAME)
 *                     .location(origin).count(5).spawn())
 *     .delay(10)
 *     .step(() -> JavaSoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).playTo(player))
 *     .delay(5)
 *     .step(() -> JavaParticleBuilder.of(Particle.END_ROD)
 *                     .location(origin).count(10).spawn())
 *     .start(scheduler);
 *
 * // Cancel early:
 * handle.close();
 * ```
 */
@SculkStable
public class JavaSequence private constructor() {
    private val builder = SequenceBuilder()

    public companion object {
        /**
         * Creates a new [JavaSequence] builder.
         */
        @SculkStable
        @JvmStatic
        public fun builder(): JavaSequence = JavaSequence()
    }

    /**
     * Adds an [action] step at the current cursor position.
     */
    @SculkStable
    public fun step(action: Runnable): JavaSequence {
        builder.step { action.run() }
        return this
    }

    /**
     * Advances the cursor by [ticks] before the next step.
     */
    @SculkStable
    public fun delay(ticks: Int): JavaSequence {
        builder.delay(ticks)
        return this
    }

    /**
     * Starts the sequence using [scheduler].
     *
     * Returns a [SculkHandle] that cancels all pending steps when closed.
     */
    @SculkStable
    public fun start(scheduler: SculkScheduler): SculkHandle = builder.build().start(scheduler)
}
