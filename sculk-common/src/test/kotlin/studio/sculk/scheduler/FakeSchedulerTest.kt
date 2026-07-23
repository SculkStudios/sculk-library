package studio.sculk.scheduler

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.SculkHandle

class FakeSchedulerTest {
    @Test
    fun `runAsyncResult completes with task value`() {
        val scheduler = ImmediateScheduler()

        val value = scheduler.runAsyncResult { "loaded" }.join()

        assertEquals("loaded", value)
    }

    @Test
    fun `asyncThenSync hands result back to sync context`() {
        val scheduler = ImmediateScheduler()
        var result = ""

        scheduler.asyncThenSync(entity = org.mockito.kotlin.mock<Entity>(), async = { "profile" }, sync = { result = it })

        assertEquals("profile", result)
    }

    @Test
    fun `runNow schedules when the caller does not own the thread`() {
        val scheduler = CountingScheduler(owns = false)

        scheduler.runNow { }
        scheduler.runNow(org.mockito.kotlin.mock<Entity>()) { }

        assertEquals(2, scheduler.scheduled)
    }

    @Test
    fun `runNow runs inline when the caller already owns the thread`() {
        val scheduler = CountingScheduler(owns = true)
        var ran = 0

        scheduler.runNow { ran++ }
        scheduler.runNow(org.mockito.kotlin.mock<Entity>()) { ran++ }

        assertEquals(2, ran)
        assertEquals(0, scheduler.scheduled)
    }

    private class CountingScheduler(private val owns: Boolean) : SculkScheduler {
        var scheduled: Int = 0
            private set

        override fun ownsGlobalThread(): Boolean = owns

        override fun ownsThread(entity: Entity): Boolean = owns

        override fun runSync(task: Runnable): SculkHandle {
            scheduled++
            return SculkHandle {}
        }

        override fun runSync(entity: Entity, task: Runnable): SculkHandle = runSync(task)

        override fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = runSync(task)

        override fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle = runSync(task)

        override fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = runSync(task)

        override fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = runSync(task)
    }

    private class ImmediateScheduler : SculkScheduler {
        override fun runSync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSync(entity: Entity, task: Runnable): SculkHandle = runSync(task)

        override fun runSync(location: Location, task: Runnable): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = runAsync(task)

        override fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = runAsync(task)
    }
}
