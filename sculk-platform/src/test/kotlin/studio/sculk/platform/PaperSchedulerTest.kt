package studio.sculk.platform

import org.bukkit.Bukkit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import studio.sculk.annotation.SculkInternal
import java.util.concurrent.atomic.AtomicInteger

/**
 * The three edges Folia has that Bukkit does not.
 *
 * A non-positive delay or period throws there rather than meaning "next tick", and the entity
 * scheduler drops work silently when handed a null retired callback. All three are normalised in
 * `PaperScheduler` so a delay computed from a config value cannot take the server down, and none of
 * them are reachable from `FakeScheduler` — this is the only place they get exercised.
 */
@OptIn(SculkInternal::class)
class PaperSchedulerTest {
    private lateinit var server: ServerMock
    private lateinit var scheduler: PaperScheduler

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        scheduler = PaperScheduler(MockBukkit.createMockPlugin())
    }

    @AfterEach
    fun tearDown() = MockBukkit.unmock()

    private fun tick(times: Int) = server.scheduler.performTicks(times.toLong())

    @Test
    fun `a zero delay means next tick rather than an exception`() {
        val runs = AtomicInteger()

        scheduler.runSyncDelayed(0, runs::incrementAndGet)
        tick(2)

        assertEquals(1, runs.get())
    }

    @Test
    fun `a negative delay is treated as next tick`() {
        val runs = AtomicInteger()

        // Delays are computed from config values, and a negative one is a typo, not a reason to
        // throw out of whatever scheduled it.
        scheduler.runSyncDelayed(-20, runs::incrementAndGet)
        tick(2)

        assertEquals(1, runs.get())
    }

    @Test
    fun `a zero period is floored to one tick rather than rejected`() {
        val runs = AtomicInteger()

        scheduler.runSyncRepeating(0, 0, runs::incrementAndGet)
        tick(3)

        assertTrue(runs.get() >= 2, "a floored period should have fired repeatedly, got ${runs.get()}")
    }

    @Test
    fun `a repeating task stops when its handle is closed`() {
        val runs = AtomicInteger()
        val handle = scheduler.runSyncRepeating(1, 1, runs::incrementAndGet)

        tick(3)
        val beforeClose = runs.get()
        handle.close()
        tick(5)

        assertEquals(beforeClose, runs.get(), "cancelling must actually stop the task")
    }

    @Test
    fun `an async task runs off the main thread`() {
        val onMain = java.util.concurrent.atomic.AtomicBoolean(true)

        scheduler.runAsync { onMain.set(Bukkit.isPrimaryThread()) }
        tick(2)
        server.scheduler.waitAsyncTasksFinished()

        assertEquals(false, onMain.get())
    }

    @Test
    fun `a zero async delay does not throw`() {
        val runs = AtomicInteger()

        scheduler.runAsyncDelayed(0, runs::incrementAndGet)
        tick(2)
        server.scheduler.waitAsyncTasksFinished()

        assertEquals(1, runs.get())
    }

    @Test
    fun `the main thread owns the global region`() {
        // On Paper this is isPrimaryThread; on Folia it is isGlobalTickThread. Getting it wrong
        // makes runNow always schedule, costing a tick on every call that was already on-thread.
        assertTrue(scheduler.ownsGlobalThread())
    }

    @Test
    fun `runNow executes inline when the caller already owns the thread`() {
        val runs = AtomicInteger()

        scheduler.runNow(runs::incrementAndGet)

        // No tick driven: inline means inline, which is the entire point of the method.
        assertEquals(1, runs.get())
    }
}
