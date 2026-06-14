package studio.sculk.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.SculkHandle
import studio.sculk.scheduler.SculkScheduler

@OptIn(studio.sculk.annotation.SculkInternal::class)
class BatchTickerTest {
    /** Captures the repeating task so the test can pump ticks deterministically. */
    private class CapturingScheduler : SculkScheduler {
        var repeating: Runnable? = null

        override fun runSync(task: Runnable): SculkHandle = SculkHandle {}

        override fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = SculkHandle {}

        override fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle {
            repeating = task
            return SculkHandle {}
        }

        override fun runAsync(task: Runnable): SculkHandle = SculkHandle {}

        override fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = SculkHandle {}

        override fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = SculkHandle {}
    }

    @Test
    fun `visits every element exactly once per cycle`() {
        val scheduler = CapturingScheduler()
        val data = (1..5).toList()
        val visits = mutableListOf<Int>()
        BatchTicker(scheduler, source = { data }, batchSize = 2, action = { visits += it })

        // ceil(5 / 2) = 3 runs make a full cycle.
        repeat(3) { scheduler.repeating?.run() }

        assertEquals(listOf(1, 2, 3, 4, 5), visits)
    }

    @Test
    fun `large batch size processes everything in one run`() {
        val scheduler = CapturingScheduler()
        val data = (1..4).toList()
        val visits = mutableListOf<Int>()
        BatchTicker(scheduler, source = { data }, batchSize = 100, action = { visits += it })

        scheduler.repeating?.run()

        assertEquals(listOf(1, 2, 3, 4), visits)
    }

    @Test
    fun `empty source does not invoke the action`() {
        val scheduler = CapturingScheduler()
        var calls = 0
        BatchTicker<Int>(scheduler, source = { emptyList() }, batchSize = 8, action = { calls++ })

        repeat(3) { scheduler.repeating?.run() }

        assertEquals(0, calls)
    }

    @Test
    fun `shrinking source between runs does not over-read`() {
        val scheduler = CapturingScheduler()
        var data = (1..6).toList()
        val visits = mutableListOf<Int>()
        BatchTicker(scheduler, source = { data }, batchSize = 4, action = { visits += it })

        scheduler.repeating?.run() // visits 1,2,3,4 -> cursor 4
        data = listOf(10, 11) // source shrank below the cursor
        scheduler.repeating?.run() // cursor clamps to 0, visits 10,11

        assertEquals(listOf(1, 2, 3, 4, 10, 11), visits)
    }
}
