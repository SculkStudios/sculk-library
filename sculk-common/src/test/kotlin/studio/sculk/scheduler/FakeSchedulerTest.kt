package studio.sculk.scheduler

import org.bukkit.entity.Entity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FakeSchedulerTest {
    @Test
    fun `undelayed work runs inline`() {
        val scheduler = FakeScheduler()
        var ran = false

        scheduler.runSync { ran = true }

        assertTrue(ran)
        assertEquals(0, scheduler.pending.size)
    }

    @Test
    fun `a delayed task does not run until the clock reaches it`() {
        val scheduler = FakeScheduler()
        var ran = false

        scheduler.runSyncDelayed(5) { ran = true }

        scheduler.advance(4)
        assertEquals(false, ran, "four ticks is not five")

        scheduler.advance(1)
        assertTrue(ran)
        assertEquals(0, scheduler.pending.size, "a one-shot task is dropped after it fires")
    }

    @Test
    fun `a repeating task fires once per period rather than once per advance`() {
        val scheduler = FakeScheduler()
        var runs = 0

        scheduler.runSyncRepeating(delayTicks = 0, periodTicks = 5) { runs++ }

        scheduler.advance(10)

        assertEquals(2, runs)
        assertEquals(1, scheduler.pending.size, "a repeating task stays queued")
    }

    @Test
    fun `closing a handle stops a repeating task`() {
        val scheduler = FakeScheduler()
        var runs = 0

        val handle = scheduler.runSyncRepeating(delayTicks = 0, periodTicks = 1) { runs++ }
        scheduler.advance(2)
        handle.close()
        scheduler.advance(5)

        assertEquals(2, runs)
        assertEquals(0, scheduler.pending.size)
    }

    @Test
    fun `a non-positive delay still lands on the next tick rather than never`() {
        val scheduler = FakeScheduler()
        var runs = 0

        scheduler.runSyncDelayed(0) { runs++ }
        scheduler.runSyncDelayed(-5) { runs++ }

        scheduler.advance(1)

        assertEquals(2, runs)
    }

    @Test
    fun `a zero period is floored to one tick`() {
        val scheduler = FakeScheduler()
        var runs = 0

        scheduler.runSyncRepeating(delayTicks = 0, periodTicks = 0) { runs++ }

        scheduler.advance(3)

        assertEquals(3, runs)
    }

    @Test
    fun `runNow runs inline when the caller already owns the thread`() {
        val scheduler = FakeScheduler().apply {
            ownsGlobal = true
            ownsRegions = true
        }
        var ran = 0

        scheduler.runNow { ran++ }
        scheduler.runNow(mock<Entity>()) { ran++ }

        assertEquals(2, ran)
    }

    @Test
    fun `runNow schedules when the caller does not own the thread`() {
        val scheduler = FakeScheduler().apply {
            ownsGlobal = false
            ownsRegions = false
        }
        var ran = 0

        scheduler.runNow { ran++ }
        scheduler.runNow(mock<Entity>()) { ran++ }

        // FakeScheduler runs undelayed sync work inline, so the effect is the same; what this
        // pins is that runNow consults owns* rather than always taking the inline path.
        assertEquals(2, ran)
        assertEquals(2, scheduler.executions, "both went through runSync rather than running directly")
    }

    @Test
    fun `clear resets the clock and the queue`() {
        val scheduler = FakeScheduler()
        scheduler.runSyncDelayed(5) { }
        scheduler.advance(2)

        scheduler.clear()

        assertEquals(0, scheduler.currentTick)
        assertEquals(0, scheduler.executions)
        assertEquals(0, scheduler.pending.size)
    }
}
