package studio.sculk.coroutine

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkHandle
import studio.sculk.scheduler.SculkScheduler
import java.util.concurrent.atomic.AtomicInteger

class SculkCoroutineScopeTest {
    @Test
    fun `launchMain dispatches through the sync scheduler`() {
        val scheduler = RecordingScheduler()
        val scope = SculkCoroutineScope(scheduler)

        runBlocking { scope.launchMain { }.join() }

        assertTrue(scheduler.syncCount.get() > 0, "expected at least one sync dispatch")
        assertEquals(0, scheduler.asyncCount.get())
    }

    @Test
    fun `launchAsync dispatches through the async scheduler`() {
        val scheduler = RecordingScheduler()
        val scope = SculkCoroutineScope(scheduler)

        runBlocking { scope.launchAsync { }.join() }

        assertTrue(scheduler.asyncCount.get() > 0, "expected at least one async dispatch")
        assertEquals(0, scheduler.syncCount.get())
    }

    @Test
    fun `withMain switches onto the sync scheduler and returns the value`() {
        val scheduler = RecordingScheduler()
        val scope = SculkCoroutineScope(scheduler)

        val result = runBlocking { scope.withAsync { scope.withMain { "ok" } } }

        assertEquals("ok", result)
        assertTrue(scheduler.syncCount.get() > 0)
    }

    @Test
    fun `close cancels the scope so new work does not run`() {
        val scheduler = RecordingScheduler()
        val scope = SculkCoroutineScope(scheduler)

        scope.close()
        val job: Job = scope.launchAsync { error("should never execute") }

        assertTrue(job.isCancelled)
        assertFalse(job.isActive)
    }

    /** Scheduler that runs every task inline and records which surface dispatched it. */
    private class RecordingScheduler : SculkScheduler {
        val syncCount = AtomicInteger(0)
        val asyncCount = AtomicInteger(0)

        override fun runSync(task: Runnable): SculkHandle {
            syncCount.incrementAndGet()
            task.run()
            return SculkHandle {}
        }

        override fun runSyncDelayed(
            delayTicks: Long,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSyncRepeating(
            delayTicks: Long,
            periodTicks: Long,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSync(
            entity: Entity,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSync(
            location: Location,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle {
            asyncCount.incrementAndGet()
            task.run()
            return SculkHandle {}
        }

        override fun runAsyncDelayed(
            delayTicks: Long,
            task: Runnable,
        ): SculkHandle = runAsync(task)

        override fun runAsyncRepeating(
            delayTicks: Long,
            periodTicks: Long,
            task: Runnable,
        ): SculkHandle = runAsync(task)
    }
}
