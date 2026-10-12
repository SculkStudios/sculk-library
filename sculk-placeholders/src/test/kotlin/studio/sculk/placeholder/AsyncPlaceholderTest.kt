package studio.sculk.placeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkScope
import studio.sculk.getOrNull
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeExecutor
import studio.sculk.text.Text
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class AsyncPlaceholderTest {
    private val clock = FakeClock()
    private val executor = FakeExecutor()
    private val failures = mutableListOf<Throwable>()
    private val scope = SculkScope.root("plugin", executor) { failures += it }
    private val placeholders = PlaceholderRegistry(scope, clock)
    private val text = Text()
    private val steve = Viewer("Steve")

    private var stored = 100
    private var loads = 0

    private fun balance(refresh: Refresh = Refresh.Every(10.seconds)) = placeholders.audienceAsync("shop:balance", refresh, initial = -1) {
        loads++
        stored
    }

    @Test
    fun `a render never waits for a load and shows the initial value until the first one arrives`() {
        balance()
        assertEquals("-1", text.render("<shop_balance>", placeholders.resolver(steve)).plain())
        assertEquals(0, loads, "nothing has loaded until the async context runs")
        executor.tick()
        assertEquals(1, loads)
        assertEquals("100", text.render("<shop_balance>", placeholders.resolver(steve)).plain())
    }

    @Test
    fun `a load runs in the async context, never on the rendering thread`() {
        val contexts = mutableListOf<Boolean>()
        val motd = placeholders.globalAsync("server:motd", Refresh.UntilInvalidated) {
            contexts += executor.owns(ExecutionContext.Async)
            "hello"
        }
        motd.value()
        executor.tick()
        assertEquals(listOf(true), contexts)
        assertEquals("hello", motd.value().getOrNull())
    }

    @Test
    fun `a stale value stays shown while a newer one loads`() {
        val balance = balance()
        balance.value(steve)
        executor.tick()
        stored = 250
        clock.advance(Duration.ofSeconds(10))
        assertEquals(100, balance.value(steve).getOrNull(), "the stale value is shown while the reload runs")
        executor.tick()
        assertEquals(250, balance.value(steve).getOrNull())
        assertEquals(2, loads)
    }

    @Test
    fun `only one load runs per value at a time`() {
        val balance = balance(Refresh.Live)
        repeat(5) { balance.value(steve) }
        executor.tick()
        assertEquals(1, loads)
        repeat(5) { balance.value(steve) }
        executor.tick()
        assertEquals(2, loads, "a live async value reloads on the next render after each load")
    }

    @Test
    fun `invalidating starts a reload on the next render and keeps the old value shown meanwhile`() {
        val balance = balance(Refresh.UntilInvalidated)
        balance.value(steve)
        executor.tick()
        stored = 5
        balance.invalidate(steve)
        assertEquals(100, balance.value(steve).getOrNull())
        executor.tick()
        assertEquals(5, balance.value(steve).getOrNull())
    }

    @Test
    fun `a load requested before an invalidation is followed by another`() {
        val balance = balance(Refresh.UntilInvalidated)
        balance.value(steve)
        balance.invalidate(steve)
        executor.tick()
        assertEquals(100, balance.value(steve).getOrNull())
        executor.tick()
        assertEquals(2, loads, "the first result may predate the invalidation, so it does not count as fresh")
        balance.value(steve)
        executor.tick()
        assertEquals(2, loads)
    }

    @Test
    fun `a failed load is reported, keeps the last value, and waits before trying again`() {
        var fail = false
        val balance = placeholders.audienceAsync("shop:balance", Refresh.Live, initial = 0) {
            loads++
            if (fail) error("database down") else stored
        }
        balance.value(steve)
        executor.tick()
        fail = true
        balance.value(steve)
        executor.tick()
        assertEquals("database down", failures.single().message)
        assertEquals(100, balance.value(steve).getOrNull())
        executor.tick()
        assertEquals(2, loads, "no retry inside the back-off")
        clock.advance(Duration.ofSeconds(5))
        fail = false
        stored = 7
        balance.value(steve)
        executor.tick()
        assertEquals(7, balance.value(steve).getOrNull())
    }

    @Test
    fun `nothing loads once the scope has closed`() {
        val balance = balance()
        scope.close()
        assertEquals(-1, balance.value(steve).getOrNull())
        assertEquals(0, executor.pendingCount)
        assertTrue(failures.isEmpty())
    }
}
