package gg.sculk.effects

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.scheduler.SculkScheduler
import java.util.concurrent.atomic.AtomicInteger

/**
 * A sequential series of animation steps with configurable delays between them.
 *
 * Unlike [AnimationTimeline] (which uses absolute tick offsets), [AnimationSequence]
 * uses relative delays — each [SequenceBuilder.delay] advances the cursor by that many ticks.
 *
 * Example:
 * ```kotlin
 * val seq = sequence {
 *     step  { particle(Particle.FLAME) { location = origin; count = 5; spawn() } }
 *     delay(10)
 *     step  { sound(Sound.ENTITY_PLAYER_LEVELUP) { playTo(player) } }
 *     delay(5)
 *     step  { particle(Particle.END_ROD) { location = origin; count = 10; spawn() } }
 * }
 * val handle = seq.start(scheduler)
 * ```
 */
@SculkStable
public class AnimationSequence internal constructor(
    private val steps: List<SequenceEntry>,
) {
    /**
     * Starts the sequence using [scheduler].
     *
     * Returns a [SculkHandle] that cancels any pending steps when closed.
     */
    @SculkStable
    public fun start(scheduler: SculkScheduler): SculkHandle {
        if (steps.isEmpty()) return SculkHandle {}

        val handles = mutableListOf<SculkHandle>()
        val cancelled = AtomicInteger(0)
        var cursor = 0L

        for (entry in steps) {
            when (entry) {
                is SequenceEntry.Step -> {
                    val tick = cursor
                    val h =
                        scheduler.runSyncDelayed(tick) {
                            if (cancelled.get() == 0) entry.action()
                        }
                    handles += h
                }
                is SequenceEntry.Delay -> cursor += entry.ticks
            }
        }

        return SculkHandle {
            cancelled.set(1)
            handles.forEach { it.close() }
        }
    }
}

internal sealed interface SequenceEntry {
    data class Step(
        val action: () -> Unit,
    ) : SequenceEntry

    data class Delay(
        val ticks: Long,
    ) : SequenceEntry
}

/**
 * DSL builder for [AnimationSequence].
 */
@SculkStable
public class SequenceBuilder {
    private val entries = mutableListOf<SequenceEntry>()

    /**
     * Adds an action step at the current cursor position.
     */
    @SculkStable
    public fun step(action: () -> Unit) {
        entries += SequenceEntry.Step(action)
    }

    /**
     * Advances the cursor by [ticks] before the next step.
     */
    @SculkStable
    public fun delay(ticks: Int) {
        entries += SequenceEntry.Delay(ticks.toLong())
    }

    internal fun build(): AnimationSequence = AnimationSequence(entries.toList())
}

/**
 * Creates an [AnimationSequence] from [block].
 *
 * Start playback by calling [AnimationSequence.start] with a [SculkScheduler].
 */
@SculkStable
public fun sequence(block: SequenceBuilder.() -> Unit): AnimationSequence = SequenceBuilder().apply(block).build()
