package gg.sculk.effects

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.scheduler.SculkScheduler
import java.util.concurrent.atomic.AtomicInteger

/**
 * An immutable sequence of timed animation steps.
 *
 * Build with [timeline] and start playback via [start].
 *
 * Example:
 * ```kotlin
 * val tl = timeline {
 *     at(0)  { particle(Particle.FLAME) { location = origin; count = 10; spawn() } }
 *     at(10) { sound(Sound.ENTITY_PLAYER_LEVELUP) { playTo(player) } }
 *     at(20) { particle(Particle.END_ROD) { location = origin; count = 30; spawn() } }
 *     loop(3)
 * }
 * val handle = tl.start(scheduler)
 * // cancel early:
 * handle.close()
 * ```
 */
@SculkStable
public class AnimationTimeline internal constructor(
    private val steps: List<TimelineStep>,
    private val loopCount: Int,
) {
    /**
     * Starts the timeline using [scheduler].
     *
     * Returns a [SculkHandle] that, when closed, cancels any remaining scheduled steps.
     * If [loopCount] is 0 the timeline plays once; otherwise it plays [loopCount] times.
     */
    @SculkStable
    public fun start(scheduler: SculkScheduler): SculkHandle {
        if (steps.isEmpty()) return SculkHandle {}

        val totalDuration = steps.maxOf { it.tick } + 1L
        val plays = if (loopCount <= 0) 1 else loopCount

        val handles = mutableListOf<SculkHandle>()
        val cancelled = AtomicInteger(0)

        for (loop in 0 until plays) {
            val loopOffset = loop * totalDuration
            for (step in steps) {
                val delay = loopOffset + step.tick
                val h =
                    scheduler.runSyncDelayed(delay) {
                        if (cancelled.get() == 0) step.action()
                    }
                handles += h
            }
        }

        return SculkHandle {
            cancelled.set(1)
            handles.forEach { it.close() }
        }
    }
}

/** A single timed action at a specific [tick] offset. */
internal data class TimelineStep(
    val tick: Long,
    val action: () -> Unit,
)

/**
 * DSL builder for [AnimationTimeline].
 */
@SculkStable
public class TimelineBuilder {
    private val steps = mutableListOf<TimelineStep>()
    private var loopCount: Int = 1

    /**
     * Registers an [action] to execute at [tick] ticks after the timeline starts.
     */
    @SculkStable
    public fun at(
        tick: Int,
        action: () -> Unit,
    ) {
        steps += TimelineStep(tick.toLong(), action)
    }

    /**
     * Sets how many times the timeline repeats. Defaults to 1 (play once).
     */
    @SculkStable
    public fun loop(times: Int) {
        loopCount = times
    }

    internal fun build(): AnimationTimeline = AnimationTimeline(steps.toList(), loopCount)
}

/**
 * Creates an [AnimationTimeline] from [block].
 *
 * Start playback by calling [AnimationTimeline.start] with a [SculkScheduler].
 */
@SculkStable
public fun timeline(block: TimelineBuilder.() -> Unit): AnimationTimeline = TimelineBuilder().apply(block).build()
