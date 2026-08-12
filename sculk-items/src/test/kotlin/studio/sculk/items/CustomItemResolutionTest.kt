package studio.sculk.items

import org.bukkit.Material
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import studio.sculk.SculkResult
import java.util.logging.Handler
import java.util.logging.LogRecord

/**
 * A custom-item key reaches its plugin, and everything else resolves exactly as it did.
 *
 * The custom-item plugins themselves are proprietary and are not on the classpath, so the success
 * path cannot be exercised anywhere but a real server. What *can* be pinned is the dispatch — which
 * keys are claimed, which are not, and what a server owner is told when the plugin is missing.
 */
class CustomItemResolutionTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() = MockBukkit.unmock()

    @Test
    fun `a custom key reports the plugin it needs`() {
        val result = item("nexo:ruby_sword")

        assertTrue(result is SculkResult.Failure)
        assertEquals(
            "Nexo is not installed or is not enabled, so 'nexo:ruby_sword' cannot be resolved.",
            (result as SculkResult.Failure).message,
        )
    }

    @Test
    fun `each provider prefix reaches its own plugin`() {
        assertFailureMessage("oraxen:ruby_sword", "Oraxen is not installed or is not enabled, so 'oraxen:ruby_sword' cannot be resolved.")
        assertFailureMessage(
            "itemsadder:mypack:ruby",
            "ItemsAdder is not installed or is not enabled, so 'itemsadder:mypack:ruby' cannot be resolved.",
        )
    }

    @Test
    fun `vanilla keys resolve exactly as before`() {
        assertEquals(Material.DIAMOND_SWORD, item("diamond_sword").getOrThrow().type)
        assertEquals(Material.DIAMOND_SWORD, item("minecraft:diamond_sword").getOrThrow().type)
        assertEquals(Material.DIAMOND, item("DIAMOND").getOrThrow().type)
    }

    @Test
    fun `nothing that resolved before can change meaning`() {
        // Material.matchMaterial strips only a literal `minecraft:` prefix and then deletes every
        // non-word character, so `nexo:ruby_sword` was already NEXORUBY_SWORD and already null.
        // A provider prefix therefore cannot be taking a key away from anything.
        assertNull(Material.matchMaterial("nexo:ruby_sword"))
        assertNull(Material.matchMaterial("oraxen:ruby_sword"))
        assertNull(Material.matchMaterial("itemsadder:mypack:ruby"))
    }

    @Test
    fun `a custom key is never quietly downgraded to the vanilla material of the same name`() {
        // materialByKey normalises with substringAfter(':'), which strips any namespace at all —
        // through it `nexo:diamond` becomes DIAMOND. Resolution must not go anywhere near it, or a
        // server missing Nexo hands out a plain diamond and nobody finds out.
        assertEquals(Material.DIAMOND, materialByKey("nexo:diamond"), "the hazard this guards is still there")

        val result = item("nexo:diamond")

        assertTrue(result is SculkResult.Failure, "a custom key must fail loudly, not fall back to vanilla")
        assertTrue((result as SculkResult.Failure).message.startsWith("Nexo is not installed"))
    }

    @Test
    fun `an unknown material still reports the key that was wrong`() {
        val result = item("not_a_real_material")

        assertTrue(result is SculkResult.Failure)
        assertEquals("No material named 'not_a_real_material'.", (result as SculkResult.Failure).message)
    }

    @Test
    fun `a descriptor naming a custom item fails rather than vanishing`() {
        val result = ItemDescriptor(material = "oraxen:ruby_sword", name = "<red>Ruby").toItemStack()

        assertTrue(result is SculkResult.Failure)
        assertTrue((result as SculkResult.Failure).message.startsWith("Oraxen is not installed"))
    }

    @Test
    fun `the reason is logged, once per key`() {
        // A GUI slot resolves a config item with getOrNull(), so the failure is otherwise invisible:
        // the icon is simply absent and nothing says which key or which plugin.
        val records = mutableListOf<LogRecord>()
        val handler =
            object : Handler() {
                override fun publish(record: LogRecord) {
                    records += record
                }

                override fun flush() = Unit

                override fun close() = Unit
            }
        itemLogger.addHandler(handler)
        try {
            // A key used by no other test: the warned set is process-wide and never cleared, which is
            // the point of it.
            repeat(3) { item("nexo:log_once_probe") }
        } finally {
            itemLogger.removeHandler(handler)
        }

        val ours = records.map { it.message }.filter { "log_once_probe" in it }
        assertEquals(listOf("Nexo is not installed or is not enabled, so 'nexo:log_once_probe' cannot be resolved."), ours)
    }

    private fun assertFailureMessage(key: String, expected: String) {
        val result = item(key)

        assertTrue(result is SculkResult.Failure, "expected '$key' to fail")
        assertEquals(expected, (result as SculkResult.Failure).message)
    }
}
