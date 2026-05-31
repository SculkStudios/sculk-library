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

    private class ImmediateScheduler : SculkScheduler {
        override fun runSync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncDelayed(
            delayTicks: Long,
            task: Runnable,
        ): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncRepeating(
            delayTicks: Long,
            periodTicks: Long,
            task: Runnable,
        ): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSync(
            entity: Entity,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSync(
            location: Location,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle {
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
