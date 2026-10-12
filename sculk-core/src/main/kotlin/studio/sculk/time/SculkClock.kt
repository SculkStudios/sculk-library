package studio.sculk.time

import studio.sculk.annotation.SculkStable
import java.time.Instant

/**
 * Time as Sculk reads it. Everything that waits, expires or timestamps takes a clock rather than
 * reading the system, so tests can control it.
 */
@SculkStable
public interface SculkClock {
    /** Wall-clock time, for timestamps and calendars. */
    public fun now(): Instant

    /** A monotonic reading in nanoseconds, for measuring elapsed time. Only differences are meaningful. */
    public fun monotonicNanos(): Long

    @SculkStable
    public companion object {
        public val SYSTEM: SculkClock = object : SculkClock {
            override fun now(): Instant = Instant.now()

            override fun monotonicNanos(): Long = System.nanoTime()

            override fun toString(): String = "SculkClock.SYSTEM"
        }
    }
}
