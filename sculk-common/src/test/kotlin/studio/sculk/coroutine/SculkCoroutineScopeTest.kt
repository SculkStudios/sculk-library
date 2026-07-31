package studio.sculk.coroutine

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkHandle
import studio.sculk.scheduler.FakeScheduler
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

    /**
     * Counts which surface a dispatch went through, delegating the actual behaviour to
     * [FakeScheduler] so this test only states the part it cares about.
     */
    private class RecordingScheduler(private val delegate: FakeScheduler = FakeScheduler()) : SculkScheduler by delegate {
        val syncCount = AtomicInteger(0)
        val asyncCount = AtomicInteger(0)

        override fun runSync(task: Runnable): SculkHandle {
            syncCount.incrementAndGet()
            return delegate.runSync(task)
        }

        override fun runAsync(task: Runnable): SculkHandle {
            asyncCount.incrementAndGet()
            return delegate.runAsync(task)
        }
    }
}
