package studio.sculk.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkScope
import studio.sculk.execution.delayTicks
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeExecutor

class TasksTest {
    private val executor = FakeExecutor()
    private val failures = mutableListOf<Throwable>()
    private val scope = SculkScope.root("plugin", executor) { failures += it }

    @Test
    fun `repeating work runs on its period and stops with its job`() {
        val runs = mutableListOf<Long>()
        val job = scope.repeating(periodTicks = 10, initialDelayTicks = 5) { runs += executor.currentTick }
        executor.tick(40)
        job.cancel()
        executor.tick(40)
        assertEquals(listOf(5L, 15L, 25L, 35L), runs)
    }

    @Test
    fun `a repeating run that throws is reported and the schedule continues`() {
        var runs = 0
        scope.repeating(periodTicks = 1) {
            runs++
            if (runs == 2) error("one bad run")
        }
        executor.tick(4)
        assertEquals(4, runs)
        assertEquals(listOf("one bad run"), failures.map { it.message })
    }

    @Test
    fun `delayed work of zero ticks started from its own context waits for the next tick`() {
        val ranAt = mutableListOf<Long>()
        scope.delayed(1) { scope.delayed(0) { ranAt += executor.currentTick } }
        executor.tick(1)
        assertEquals(emptyList<Long>(), ranAt)
        executor.tick(1)
        assertEquals(listOf(2L), ranAt)
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
    fun `a trigger during a run never cancels it, and schedules one more run after it`() {
        val finished = mutableListOf<Long>()
        val debouncer = Debouncer(scope, waitTicks = 2) {
            delayTicks(5)
            finished += executor.currentTick
        }
        debouncer.trigger()
        executor.tick(4)
        debouncer.trigger()
        executor.tick(20)
        assertEquals(listOf(7L, 12L), finished)
    }

    @Test
    fun `a throttler drops triggers inside its interval`() {
        val clock = FakeClock()
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
}
