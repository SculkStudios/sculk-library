package studio.sculk.placeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.execution.SculkScope
import studio.sculk.testkit.FakeExecutor
import studio.sculk.text.Text
import kotlin.time.Duration.Companion.minutes

class PlaceholderRegistryTest {
    private val scope = SculkScope.root("plugin", FakeExecutor()) {}
    private val placeholders = PlaceholderRegistry(scope)
    private val text = Text()

    @Test
    fun `a key is namespaced and becomes a tag joined by an underscore`() {
        val key = PlaceholderKey.parse("my-shop:top_balance")
        assertEquals("my-shop", key.namespace)
        assertEquals("top_balance", key.name)
        assertEquals("my-shop_top_balance", key.tag)
        assertEquals(PlaceholderKey.of("my-shop", "top_balance"), key)
    }

    @Test
    fun `a malformed key, or one whose tag would hide a MiniMessage tag, is refused`() {
        listOf("balance", ":balance", "Shop:balance", "my_shop:balance", "shop:Balance", "shop:").forEach {
            assertThrows<IllegalArgumentException>(it) { PlaceholderKey.parse(it) }
        }
        assertThrows<IllegalArgumentException> { PlaceholderKey.parse("translate:or") }
    }

    @Test
    fun `registering a key twice is a wiring bug, whatever the kind`() {
        placeholders.global("shop:balance") { 1 }
        val failure = assertThrows<IllegalStateException> { placeholders.audience("shop:balance") { 2 } }
        assertEquals("Placeholder shop:balance is already registered; register each key once.", failure.message)
    }

    @Test
    fun `closing a placeholder unregisters it and frees its key`() {
        val first = placeholders.global("shop:balance") { 1 }
        first.close()
        assertTrue(first.isClosed)
        assertEquals("<shop_balance>", text.render("<shop_balance>", placeholders.resolver()).plain())
        placeholders.global("shop:balance") { 2 }
        assertEquals("2", text.render("<shop_balance>", placeholders.resolver()).plain())
    }

    @Test
    fun `a resolver sees placeholders registered after it was made`() {
        val resolver = placeholders.resolver()
        placeholders.global("server:online") { 5 }
        assertEquals("5", text.render("<server_online>", resolver).plain())
    }

    @Test
    fun `closing the scope unregisters every placeholder and listener, and registering afterwards throws`() {
        val balance = placeholders.audience("shop:balance") { 1 }
        val changes = mutableListOf<PlaceholderChange>()
        placeholders.onChange { changes += it }
        scope.close()
        assertTrue(balance.isClosed)
        assertEquals(emptyList<SculkPlaceholder<*>>(), placeholders.registered)
        balance.invalidate()
        assertEquals(emptyList<PlaceholderChange>(), changes)
        assertThrows<IllegalStateException> { placeholders.global("shop:other") { 1 } }
    }

    @Test
    fun `a registry made on a closed scope is a wiring bug`() {
        scope.close()
        assertThrows<IllegalStateException> { PlaceholderRegistry(scope) }
    }

    @Test
    fun `registered lists the open placeholders by tag, with their kind and policy`() {
        placeholders.relational("teams:colour") { "red" }
        placeholders.globalAsync("server:motd", Refresh.Every(1.minutes)) { "hi" }
        val listed = placeholders.registered
        assertEquals(listOf("server_motd", "teams_colour"), listed.map { it.key.tag })
        assertTrue(listed[0] is GlobalPlaceholder && listed[0].isAsync)
        assertTrue(listed[1] is RelationalPlaceholder && listed[1].refresh == Refresh.Live)
    }

    @Test
    fun `a cache must hold at least one value`() {
        assertThrows<IllegalArgumentException> { PlaceholderRegistry(scope, cacheSize = 0) }
    }
}
