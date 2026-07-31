package studio.sculk.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal
import studio.sculk.scheduler.FakeScheduler

@OptIn(SculkInternal::class)
class BatchTickerTest {
    @Test
    fun `visits every element exactly once per cycle`() {
        val scheduler = FakeScheduler()
        val data = (1..5).toList()
        val visits = mutableListOf<Int>()
        BatchTicker(scheduler, source = { data }, batchSize = 2, action = { visits += it })

        // ceil(5 / 2) = 3 runs make a full cycle.
        scheduler.advance(3)

        assertEquals(listOf(1, 2, 3, 4, 5), visits)
    }

    @Test
    fun `large batch size processes everything in one run`() {
        val scheduler = FakeScheduler()
        val data = (1..4).toList()
        val visits = mutableListOf<Int>()
        BatchTicker(scheduler, source = { data }, batchSize = 100, action = { visits += it })

        scheduler.advance(1)

        assertEquals(listOf(1, 2, 3, 4), visits)
    }

    @Test
    fun `empty source does not invoke the action`() {
        val scheduler = FakeScheduler()
        var calls = 0
        BatchTicker<Int>(scheduler, source = { emptyList() }, batchSize = 8, action = { calls++ })

        scheduler.advance(3)

        assertEquals(0, calls)
    }

    @Test
    fun `shrinking source between runs does not over-read`() {
        val scheduler = FakeScheduler()
        var data = (1..6).toList()
        val visits = mutableListOf<Int>()
        BatchTicker(scheduler, source = { data }, batchSize = 4, action = { visits += it })

        scheduler.advance(1) // visits 1,2,3,4 -> cursor 4
        data = listOf(10, 11) // source shrank below the cursor
        scheduler.advance(1) // cursor clamps to 0, visits 10,11

        assertEquals(listOf(1, 2, 3, 4, 10, 11), visits)
    }

    @Test
    fun `closing the ticker stops it`() {
        val scheduler = FakeScheduler()
        var calls = 0
        val ticker = BatchTicker(scheduler, source = { listOf(1, 2) }, batchSize = 8, action = { calls++ })

        scheduler.advance(1)
        ticker.close()
        scheduler.advance(5)

        assertEquals(2, calls, "no element is visited again after close")
    }
}
