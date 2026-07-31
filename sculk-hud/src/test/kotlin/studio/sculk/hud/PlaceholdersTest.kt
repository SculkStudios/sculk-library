package studio.sculk.hud

import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class PlaceholdersTest {
    private val placeholders = Placeholders()
    private val player: Player = mock()

    @Test
    fun `only the placeholders a template mentions are resolved`() {
        var cheapCalls = 0
        var expensiveCalls = 0
        placeholders.register("coins") {
            cheapCalls++
            "100"
        }
        placeholders.register("leaderboard") {
            expensiveCalls++
            "..."
        }

        placeholders.resolve(player, "Balance: <coins>")

        assertEquals(1, cheapCalls)
        assertEquals(0, expensiveCalls, "a placeholder the row does not mention must not be computed")
    }

    @Test
    fun `resolved values are returned by name`() {
        placeholders.register("coins") { "100" }

        assertEquals(listOf("coins" to "100"), placeholders.resolve(player, "<coins>").toList())
    }

    @Test
    fun `a row mentioning nothing allocates nothing new`() {
        placeholders.register("coins") { "100" }

        val first = placeholders.resolve(player, "static")
        val second = placeholders.resolve(player, "also static")

        assertTrue(first.isEmpty())
        assertSame(first, second, "the empty case must return the shared array")
    }

    @Test
    fun `a throwing resolver yields a question mark and does not break the frame`() {
        placeholders.register("broken") { error("boom") }
        placeholders.register("fine") { "ok" }

        val resolved = placeholders.resolve(player, "<broken> <fine>").toMap()

        assertEquals("?", resolved["broken"], "a broken placeholder is one wrong value, not a dead sidebar")
        assertEquals("ok", resolved["fine"], "and it must not stop the others resolving")
    }

    @Test
    fun `unregistering removes a placeholder`() {
        placeholders.register("coins") { "100" }
        placeholders.unregister("coins")

        assertTrue(placeholders.resolve(player, "<coins>").isEmpty())
        assertEquals(emptySet<String>(), placeholders.names)
    }

    @Test
    fun `re-registering replaces the resolver`() {
        placeholders.register("coins") { "old" }
        placeholders.register("coins") { "new" }

        assertEquals(listOf("coins" to "new"), placeholders.resolve(player, "<coins>").toList())
    }

    @Test
    fun `several placeholders in one row all resolve`() {
        placeholders.register("a") { "1" }
        placeholders.register("b") { "2" }

        assertEquals(mapOf("a" to "1", "b" to "2"), placeholders.resolve(player, "<a> and <b>").toMap())
    }
}
