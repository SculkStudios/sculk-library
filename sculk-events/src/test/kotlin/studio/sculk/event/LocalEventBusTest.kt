package studio.sculk.event

import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkScope
import studio.sculk.execution.delayTicks
import studio.sculk.execution.withGlobal
import studio.sculk.testkit.FakeExecutor
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass

class LocalEventBusTest {
    private open class Purchase(val item: String) : Cancellable {
        override var isCancelled: Boolean = false
        var price: Int = 10
    }

    private class GiftPurchase(item: String) : Purchase(item)

    private val executor = FakeExecutor()
    private val scope = SculkScope.root("plugin", executor) {}
    private val failures = mutableListOf<Pair<KClass<*>, Throwable>>()
    private val bus = LocalEventBus(scope) { type, failure -> failures += type to failure }

    @Test
    fun `immediate handlers run in priority order and can cancel the event`() {
        val order = mutableListOf<String>()
        bus.listen<Purchase>(EventPriority.HIGH) { order += "high" }
        bus.listen<Purchase>(EventPriority.LOW) {
            order += "low"
            it.isCancelled = true
        }
        val outcome = executor.run { bus.fire(Purchase("sword")) }
        assertEquals(listOf("low"), order, "a cancelled event skips later handlers that do not ask for cancelled events")
        assertTrue(outcome.isCancelled)
    }

    @Test
    fun `a handler that asks for cancelled events still sees them`() {
        var seen = false
        bus.listen<Purchase>(EventPriority.LOWEST) { it.isCancelled = true }
        bus.listen<Purchase>(includeCancelled = true) { seen = true }
        executor.run { bus.fire(Purchase("sword")) }
        assertTrue(seen)
    }

    @Test
    fun `an awaited handler may suspend and still decide the event`() {
        bus.await<Purchase> { purchase ->
            delayTicks(5)
            purchase.price = 99
        }
        val outcome = executor.run { bus.fire(Purchase("sword")) }
        assertEquals(99, outcome.price)
    }

    @Test
    fun `an awaited handler that runs past its timeout is reported and the event goes on`() {
        var after = false
        bus.await<Purchase>(timeoutMillis = 50) { awaitCancellation() }
        bus.listen<Purchase>(EventPriority.HIGH) { after = true }
        executor.run { bus.fire(Purchase("sword")) }
        assertTrue(after)
        assertTrue(failures.single().second is TimeoutException)
    }

    @Test
    fun `observers start after every decision and see the final outcome`() {
        val seenPrice = mutableListOf<Int>()
        bus.observe<Purchase> { seenPrice += it.price }
        bus.listen<Purchase>(EventPriority.HIGHEST) { it.price = 42 }
        executor.run { bus.fire(Purchase("sword")) }
        executor.tickUntil { seenPrice.isNotEmpty() }
        assertEquals(listOf(42), seenPrice)
    }

    @Test
    fun `observers skip cancelled events by default`() {
        var observed = false
        bus.listen<Purchase> { it.isCancelled = true }
        bus.observe<Purchase> { observed = true }
        executor.run { bus.fire(Purchase("sword")) }
        executor.tick(3)
        assertFalse(observed)
    }

    @Test
    fun `observers start in the context the event was fired from`() {
        var owned = false
        bus.observe<Purchase> { owned = executor.owns(ExecutionContext.Global) }
        executor.run { withGlobal { bus.fire(Purchase("sword")) } }
        executor.tickUntil { owned }
        assertTrue(owned)
    }

    @Test
    fun `a handler for a type also receives its subtypes`() {
        val items = mutableListOf<String>()
        bus.listen<Purchase> { items += it.item }
        executor.run { bus.fire(GiftPurchase("rose")) }
        assertEquals(listOf("rose"), items)
    }

    @Test
    fun `a throwing handler is reported with its event type and the others still run`() {
        var second = false
        bus.listen<Purchase>(EventPriority.LOW) { error("broken handler") }
        bus.listen<Purchase>(EventPriority.HIGH) { second = true }
        executor.run { bus.fire(Purchase("sword")) }
        assertTrue(second)
        assertEquals(Purchase::class, failures.single().first)
        assertEquals("broken handler", failures.single().second.message)
    }

    @Test
    fun `closing the handle or the scope removes the handler`() {
        var calls = 0
        val handle = bus.listen<Purchase> { calls++ }
        executor.run { bus.fire(Purchase("a")) }
        handle.close()
        executor.run { bus.fire(Purchase("b")) }
        assertEquals(1, calls)
        bus.listen<Purchase> { calls++ }
        scope.close()
        assertThrows<IllegalStateException> { bus.listen<Purchase> { calls++ } }
    }
}
