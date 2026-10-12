package studio.sculk.placeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.execution.SculkScope
import studio.sculk.getOrNull
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeExecutor
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class RefreshTest {
    private val clock = FakeClock()
    private val scope = SculkScope.root("plugin", FakeExecutor()) {}
    private val placeholders = PlaceholderRegistry(scope, clock)
    private val steve = Viewer("Steve")
    private val alex = Viewer("Alex")

    @Test
    fun `a live placeholder is computed on every render`() {
        var computed = 0
        val online = placeholders.global("server:online") { ++computed }
        assertEquals(listOf(1, 2, 3), List(3) { online.value().getOrNull() })
    }

    @Test
    fun `a value refreshed every interval is kept until the interval has passed`() {
        var computed = 0
        val balance = placeholders.audience("shop:balance", Refresh.Every(5.seconds)) { ++computed }
        assertEquals(1, balance.value(steve).getOrNull())
        clock.advance(Duration.ofMillis(4_999))
        assertEquals(1, balance.value(steve).getOrNull())
        clock.advance(Duration.ofMillis(1))
        assertEquals(2, balance.value(steve).getOrNull())
    }

    @Test
    fun `a value kept until invalidated is computed again only after an invalidation`() {
        var computed = 0
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { ++computed }
        balance.value(steve)
        clock.advance(Duration.ofDays(365))
        assertEquals(1, balance.value(steve).getOrNull())
        balance.invalidate()
        assertEquals(2, balance.value(steve).getOrNull())
    }

    @Test
    fun `invalidating one audience leaves the values of the others kept`() {
        val computed = mutableListOf<String>()
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { (audience as Viewer).name.also { computed += it } }
        balance.value(steve)
        balance.value(alex)
        balance.invalidate(steve)
        balance.value(steve)
        balance.value(alex)
        assertEquals(listOf("Steve", "Alex", "Steve"), computed)
    }

    @Test
    fun `invalidating by unique id reaches an audience whose change is known only by id`() {
        var computed = 0
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { ++computed }
        balance.value(steve)
        balance.invalidate(steve.uniqueId)
        assertEquals(2, balance.value(steve).getOrNull())
    }

    @Test
    fun `a relational value is kept per viewer and target`() {
        var computed = 0
        val colour = placeholders.relational("teams:colour", Refresh.UntilInvalidated) { ++computed }
        assertEquals(
            listOf(1, 2, 1, 2),
            listOf(steve to alex, alex to steve, steve to alex, alex to steve).map {
                colour.value(it.first, it.second).getOrNull()
            },
        )
        colour.invalidate(steve, alex)
        assertEquals(3, colour.value(steve, alex).getOrNull())
        assertEquals(2, colour.value(alex, steve).getOrNull())
    }

    @Test
    fun `an audience without a uuid pointer is kept under its own identity`() {
        val console = Console()
        var computed = 0
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { ++computed }
        assertEquals(1, balance.value(console).getOrNull())
        assertEquals(1, balance.value(console).getOrNull())
        assertEquals(2, balance.value(Console()).getOrNull())
    }

    @Test
    fun `values are bounded per placeholder, least recently used first out`() {
        val bounded = PlaceholderRegistry(scope.child("bounded"), clock, cacheSize = 2)
        val computed = mutableListOf<String>()
        val balance = bounded.audience("shop:balance", Refresh.UntilInvalidated) { (audience as Viewer).name.also { computed += it } }
        val notch = Viewer("Notch")
        listOf(steve, alex, steve, notch, steve, alex).forEach { balance.value(it) }
        assertEquals(listOf("Steve", "Alex", "Notch", "Alex"), computed)
        assertEquals(2, balance.values.size)
    }

    @Test
    fun `a computation that started earlier never replaces a value computed after it`() {
        var calls = 0
        lateinit var balance: AudiencePlaceholder<String>
        balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) {
            if (calls++ == 0) "older".also { balance.value(steve) } else "newer"
        }
        balance.value(steve)
        assertEquals("newer", balance.value(steve).getOrNull())
    }

    @Test
    fun `a refresh interval is positive and finite`() {
        assertThrows<IllegalArgumentException> { Refresh.Every(kotlin.time.Duration.ZERO) }
        assertThrows<IllegalArgumentException> { Refresh.Every(kotlin.time.Duration.INFINITE) }
    }

    @Test
    fun `a synchronous resolver that throws fails the render and keeps nothing`() {
        var fail = true
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { if (fail) error("database down") else 7 }
        assertThrows<IllegalStateException> { balance.value(steve) }
        fail = false
        assertEquals(7, balance.value(steve).getOrNull())
        assertTrue(balance.values.size == 1)
    }
}
