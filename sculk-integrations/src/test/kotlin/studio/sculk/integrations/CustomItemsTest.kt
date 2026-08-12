package studio.sculk.integrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult

/**
 * The parsing and dispatch half of custom items, which is all that can be tested here.
 *
 * Nexo, Oraxen and ItemsAdder are proprietary and deliberately absent from the classpath, so what is
 * asserted is which provider an id is addressed to, what is handed to it, and what happens when the
 * plugin is not there — the state every CI run and every server without them is in.
 */
@OptIn(studio.sculk.annotation.SculkExperimental::class)
class CustomItemsTest {
    @Test
    fun `only provider prefixes are claimed`() {
        assertTrue(CustomItems.handles("nexo:ruby_sword"))
        assertTrue(CustomItems.handles("oraxen:ruby_sword"))
        assertTrue(CustomItems.handles("itemsadder:mypack:ruby"))

        // The backward-compatibility boundary: everything that resolved as a material before still
        // never reaches a provider.
        assertFalse(CustomItems.handles("diamond_sword"))
        assertFalse(CustomItems.handles("minecraft:diamond_sword"))
        assertFalse(CustomItems.handles("someotherplugin:thing"))
        assertFalse(CustomItems.handles(""))
    }

    @Test
    fun `a prefix is matched case-insensitively and around whitespace`() {
        assertTrue(CustomItems.handles(" Nexo:Ruby_Sword "))
        assertEquals("Ruby_Sword", CustomItems.split(" Nexo:Ruby_Sword ")?.second)
    }

    @Test
    fun `an ItemsAdder id keeps its own namespace`() {
        // Split on the first colon only. ItemsAdder ids are themselves `namespace:id`, so splitting
        // on the last one would hand it `ruby` and lose the pack.
        val (provider, itemId) = requireNotNull(CustomItems.split("itemsadder:mypack:ruby"))

        assertEquals("ItemsAdder", provider.pluginName)
        assertEquals("mypack:ruby", itemId)
    }

    @Test
    fun `a single-colon id is handed over whole`() {
        assertEquals("ruby_sword", CustomItems.split("nexo:ruby_sword")?.second)
        assertNull(CustomItems.split("ruby_sword"))
    }

    @Test
    fun `an absent plugin fails by name rather than silently`() {
        val result = CustomItems.resolve("nexo:ruby_sword")

        assertTrue(result is SculkResult.Failure)
        assertEquals(
            "Nexo is not installed or is not enabled, so 'nexo:ruby_sword' cannot be resolved.",
            (result as SculkResult.Failure).message,
        )
    }

    @Test
    fun `each provider reports itself, not the first one`() {
        assertMessage("oraxen:ruby_sword", "Oraxen is not installed or is not enabled, so 'oraxen:ruby_sword' cannot be resolved.")
        assertMessage(
            "itemsadder:mypack:ruby",
            "ItemsAdder is not installed or is not enabled, so 'itemsadder:mypack:ruby' cannot be resolved.",
        )
    }

    @Test
    fun `an id addressed to nothing says what the prefixes are`() {
        assertMessage(
            "diamond_sword",
            "'diamond_sword' does not name a custom-item plugin; expected one of nexo, oraxen, itemsadder.",
        )
    }

    @Test
    fun `a prefix with no item after it is its own failure`() {
        assertMessage("nexo:", "'nexo:' names Nexo but no item after the ':'.")
    }

    private fun assertMessage(id: String, expected: String) {
        val result = CustomItems.resolve(id)

        assertTrue(result is SculkResult.Failure, "expected '$id' to fail")
        assertEquals(expected, (result as SculkResult.Failure).message)
    }
}
