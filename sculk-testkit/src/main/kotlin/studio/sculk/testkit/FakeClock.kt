package studio.sculk.testkit

import studio.sculk.annotation.SculkStable
import studio.sculk.execution.SculkExecutor
import studio.sculk.time.SculkClock
import java.time.Duration
import java.time.Instant

/**
 * A [SculkClock] that moves only when the test moves it. Wall-clock and monotonic time advance
 * together. Thread-safe.
 */
@SculkStable
public class FakeClock(start: Instant = Instant.parse("2026-01-01T00:00:00Z")) : SculkClock {
    private val lock = Any()
    private var wall = start
    private var nanos = 0L

    override fun now(): Instant = synchronized(lock) { wall }

    override fun monotonicNanos(): Long = synchronized(lock) { nanos }

    public fun advance(duration: Duration) {
        require(!duration.isNegative) { "A clock only moves forward, but was asked to move $duration." }
        synchronized(lock) {
            wall += duration
            nanos += duration.toNanos()
        }
    }

    /** Advances by [ticks] server ticks of [SculkExecutor.MILLIS_PER_TICK] each. */
    public fun advanceTicks(ticks: Long): Unit = advance(Duration.ofMillis(ticks * SculkExecutor.MILLIS_PER_TICK))
}
