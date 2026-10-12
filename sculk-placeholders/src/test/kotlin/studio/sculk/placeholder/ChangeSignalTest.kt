package studio.sculk.placeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.execution.SculkScope
import studio.sculk.getOrNull
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeExecutor
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class ChangeSignalTest {
    private val clock = FakeClock()
    private val executor = FakeExecutor()
    private val failures = mutableListOf<Throwable>()
    private val scope = SculkScope.root("plugin", executor) { failures += it }
    private val placeholders = PlaceholderRegistry(scope, clock)
    private val changes = mutableListOf<PlaceholderChange>()
    private val steve = Viewer("Steve")
    private val alex = Viewer("Alex")

    init {
        placeholders.onChange { changes += it }
    }

    @Test
    fun `an invalidation signals the audience, the pair or everyone it names`() {
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { 1 }
        val colour = placeholders.relational("teams:colour") { "red" }
        balance.invalidate(steve)
        colour.invalidate(steve, alex)
        balance.invalidate()
        val expected = listOf(
            PlaceholderChange.Audience(balance.key, steve.uniqueId),
            PlaceholderChange.Relation(colour.key, steve.uniqueId, alex.uniqueId),
            PlaceholderChange.Everyone(balance.key),
        )
        assertEquals(expected, changes)
    }

    @Test
    fun `an audience without a uuid pointer is signalled as everyone`() {
        val balance = placeholders.audience("shop:balance") { 1 }
        balance.invalidate(Console())
        assertEquals(listOf(PlaceholderChange.Everyone(balance.key)), changes)
    }

    @Test
    fun `an async load that delivers a different value signals a change, and an equal one does not`() {
        var stored = 1
        val balance = placeholders.audienceAsync("shop:balance", Refresh.Every(1.seconds)) { stored }
        balance.value(steve)
        executor.tick()
        assertEquals(listOf(PlaceholderChange.Audience(balance.key, steve.uniqueId)), changes, "the first value replaces the initial one")
        clock.advance(Duration.ofSeconds(1))
        balance.value(steve)
        executor.tick()
        assertEquals(1, changes.size, "an equal value is no change")
        stored = 2
        clock.advance(Duration.ofSeconds(1))
        balance.value(steve)
        executor.tick()
        assertEquals(2, changes.size)
    }

    @Test
    fun `a synchronous value recomputed after its interval signals when it differs`() {
        var stored = 1
        val balance = placeholders.audience("shop:balance", Refresh.Every(1.seconds)) { stored }
        balance.value(steve)
        assertEquals(emptyList<PlaceholderChange>(), changes, "the first value was shown by the render that computed it")
        stored = 2
        clock.advance(Duration.ofSeconds(1))
        balance.value(steve)
        assertEquals(listOf(PlaceholderChange.Audience(balance.key, steve.uniqueId)), changes)
    }

    @Test
    fun `a synchronous recompute after an invalidation is not signalled twice`() {
        var stored = 1
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { stored }
        balance.value(steve)
        stored = 2
        balance.invalidate(steve)
        assertEquals(2, balance.value(steve).getOrNull())
        assertEquals(1, changes.size)
    }

    @Test
    fun `a listener that throws is reported and the others still run`() {
        val seen = mutableListOf<PlaceholderChange>()
        placeholders.onChange { error("broken listener") }
        placeholders.onChange { seen += it }
        placeholders.global("server:online") { 1 }.invalidate()
        assertEquals(1, seen.size)
        assertEquals("broken listener", failures.single().message)
    }

    @Test
    fun `closing a listener's handle stops it`() {
        val seen = mutableListOf<PlaceholderChange>()
        val handle = placeholders.onChange { seen += it }
        val online = placeholders.global("server:online") { 1 }
        online.invalidate()
        handle.close()
        online.invalidate()
        assertEquals(1, seen.size)
    }

    @Test
    fun `a dependent placeholder is invalidated with its source, narrowed to the same audience`() {
        var rank = "member"
        var computed = 0
        val rankOf = placeholders.audience("perms:rank", Refresh.UntilInvalidated) { rank }
        val colour = placeholders.audience("chat:colour", Refresh.UntilInvalidated) {
            computed++
            if (rankOf.value(audience).getOrNull() == "admin") "red" else "grey"
        }
        colour.invalidateWith(rankOf)
        colour.value(steve)
        colour.value(alex)
        rank = "admin"
        rankOf.invalidate(steve)
        assertEquals("red", colour.value(steve).getOrNull())
        assertEquals("grey", colour.value(alex).getOrNull(), "alex's colour was kept")
        assertEquals(3, computed)
        assertEquals(PlaceholderChange.Audience(colour.key, steve.uniqueId), changes.last())
    }

    @Test
    fun `a global placeholder depending on an audience placeholder is invalidated for everyone`() {
        var computed = 0
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { 1 }
        val richest = placeholders.global("shop:richest", Refresh.UntilInvalidated) { ++computed }
        richest.invalidateWith(balance)
        richest.value()
        balance.invalidate(steve)
        assertEquals(2, richest.value().getOrNull())
    }

    @Test
    fun `linking placeholders in a cycle is a wiring bug`() {
        val a = placeholders.global("x:a") { 1 }
        val b = placeholders.global("x:b") { 2 }
        val c = placeholders.global("x:c") { 3 }
        b.invalidateWith(a)
        c.invalidateWith(b)
        assertThrows<IllegalArgumentException> { a.invalidateWith(c) }
        assertThrows<IllegalArgumentException> { a.invalidateWith(a) }
    }

    @Test
    fun `closing a dependent ends its link`() {
        var computed = 0
        val source = placeholders.global("x:source") { 1 }
        val dependent = placeholders.global("x:dependent", Refresh.UntilInvalidated) { ++computed }
        dependent.invalidateWith(source)
        dependent.close()
        source.invalidate()
        assertEquals(listOf(PlaceholderChange.Everyone(source.key)), changes)
    }
}
