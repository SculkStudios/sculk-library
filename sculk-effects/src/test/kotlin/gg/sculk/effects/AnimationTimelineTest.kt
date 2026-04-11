package gg.sculk.effects

import gg.sculk.core.SculkHandle
import gg.sculk.core.scheduler.SculkScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Captures scheduled tasks so tests can fire them manually. */
private class FakeScheduler : SculkScheduler {
    data class Task(
        val delay: Long,
        val action: Runnable,
    )

    val tasks = mutableListOf<Task>()

    override fun runSync(task: Runnable): SculkHandle {
        tasks += Task(0, task)
        return SculkHandle {}
    }

    override fun runSyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle {
        tasks += Task(delayTicks, task)
        return SculkHandle {}
    }

    override fun runSyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle = SculkHandle {}

    override fun runAsync(task: Runnable): SculkHandle = SculkHandle {}

    override fun runAsyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle = SculkHandle {}

    fun fireAll() = tasks.sortedBy { it.delay }.forEach { it.action.run() }
}

class AnimationTimelineTest {
    @Test
    fun `steps are scheduled at correct tick offsets`() {
        val order = mutableListOf<Int>()
        val tl =
            timeline {
                at(0) { order += 0 }
                at(10) { order += 10 }
                at(5) { order += 5 }
            }

        val scheduler = FakeScheduler()
        tl.start(scheduler)
        scheduler.fireAll()

        assertEquals(listOf(0, 5, 10), order)
    }

    @Test
    fun `loop repeats actions the given number of times`() {
        val fired = mutableListOf<Long>()
        val tl =
            timeline {
                at(0) { fired += 0 }
                at(10) { fired += 10 }
                loop(3)
            }

        val scheduler = FakeScheduler()
        tl.start(scheduler)

        // 3 loops × 2 steps = 6 scheduled tasks
        assertEquals(6, scheduler.tasks.size)
    }

    @Test
    fun `empty timeline returns immediately`() {
        val scheduler = FakeScheduler()
        timeline {}.start(scheduler)
        assertEquals(0, scheduler.tasks.size)
    }
}

class AnimationSequenceTest {
    @Test
    fun `steps fire at accumulated delay offsets`() {
        val order = mutableListOf<Int>()
        val seq =
            sequence {
                step { order += 0 }
                delay(5)
                step { order += 5 }
                delay(10)
                step { order += 15 }
            }

        val scheduler = FakeScheduler()
        seq.start(scheduler)
        scheduler.fireAll()

        assertEquals(listOf(0, 5, 15), order)

        // Verify scheduled delays
        val delays = scheduler.tasks.map { it.delay }.sorted()
        assertEquals(listOf(0L, 5L, 15L), delays)
    }

    @Test
    fun `empty sequence returns immediately`() {
        val scheduler = FakeScheduler()
        sequence {}.start(scheduler)
        assertEquals(0, scheduler.tasks.size)
    }
}
