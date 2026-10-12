package studio.sculk.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.execution.SculkScope
import studio.sculk.execution.delayTicks
import studio.sculk.testkit.FakeExecutor

/** The examples on the events page of the documentation, kept compiling and passing here. */
class EventGuideTest {
    // guide:event
    class ShopPurchase(val player: String, val item: String, var price: Int) : Cancellable {
        override var isCancelled: Boolean = false
    }
    // end

    private val executor = FakeExecutor()
    private val scope = SculkScope.root("shop", executor) {}
    private val sales = mutableListOf<String>()

    @Test
    fun `the guide's handlers decide, observe and await as the page says`() {
        // guide:handlers
        val events = LocalEventBus(scope) { type, failure -> println("A ${type.simpleName} handler failed: $failure") }

        events.listen<ShopPurchase>(EventPriority.LOW) { purchase ->
            if (purchase.item == "bedrock") purchase.isCancelled = true
        }
        events.await<ShopPurchase> { purchase ->
            delayTicks(1) // a database lookup in a real plugin
            if (purchase.player == "Steve") purchase.price = purchase.price * 9 / 10
        }
        events.observe<ShopPurchase> { purchase ->
            sales += "${purchase.player} bought ${purchase.item} for ${purchase.price}"
        }
        // end

        // guide:fire
        val purchase = executor.run { events.fire(ShopPurchase("Steve", "diamond", 100)) }
        // end

        executor.tickUntil { sales.isNotEmpty() }
        assertEquals(90, purchase.price)
        assertEquals(listOf("Steve bought diamond for 90"), sales)
        val refused = executor.run { events.fire(ShopPurchase("Alex", "bedrock", 1)) }
        assertTrue(refused.isCancelled)
    }
}
