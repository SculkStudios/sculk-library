package studio.sculk.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.FakeExecutor
import studio.sculk.execution.SculkScope
import studio.sculk.time.SculkClock
import java.time.Instant

class TasksTest {
    private val executor = FakeExecutor()
    private val scope = SculkScope.root("plugin", executor) { throw it }

    @Test
    fun `repeating work runs on its period and stops with its job`() {
        val runs = mutableListOf<Long>()
        val job = scope.repeating(periodTicks = 10, initialDelayTicks = 5) { runs += executor.currentTick }
        executor.tick(40)
        job.cancel()
        executor.tick(40)
        assertEquals(listOf(6L, 16L, 26L, 36L), runs)
    }

    @Test
    fun `delayed work runs once in the requested context`() {
        var owned = false
        scope.delayed(20, ExecutionContext.Async) { owned = executor.owns(ExecutionContext.Async) }
        executor.tick(25)
        assertTrue(owned)
    }

    @Test
    fun `a debouncer runs once after the last trigger of a burst`() {
        var runs = 0
        val debouncer = Debouncer(scope, waitTicks = 10) { runs++ }
        debouncer.trigger()
        executor.tick(5)
        debouncer.trigger()
        executor.tick(5)
        debouncer.trigger()
        executor.tick(15)
        assertEquals(1, runs)
    }

    @Test
    fun `a throttler drops triggers inside its interval`() {
        val clock = ManualClock()
        var runs = 0
        val throttler = Throttler(scope, intervalTicks = 20, clock = clock) { runs++ }
        assertTrue(throttler.trigger())
        clock.advanceTicks(10)
        assertFalse(throttler.trigger())
        clock.advanceTicks(10)
        assertTrue(throttler.trigger())
        executor.tick(3)
        assertEquals(2, runs)
    }

    @Test
    fun `batches walk a changing collection a few items per tick and start over`() {
        val seen = mutableListOf<Int>()
        val players = mutableListOf(1, 2, 3, 4, 5)
        val job = scope.batches(perTick = 2, source = { players }) { seen += it }
        executor.tick(2)
        players += 6
        executor.tick(3)
        job.cancel()
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 1, 2), seen.take(8))
    }

    private class ManualClock : SculkClock {
        private var nanos = 0L

        fun advanceTicks(ticks: Long) {
            nanos += ticks * 50_000_000L
        }

        override fun now(): Instant = Instant.EPOCH.plusNanos(nanos)

        override fun monotonicNanos(): Long = nanos
    }
}
