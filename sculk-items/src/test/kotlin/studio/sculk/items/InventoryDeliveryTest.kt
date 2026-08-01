package studio.sculk.items

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class InventoryDeliveryTest {
    private lateinit var server: ServerMock
    private lateinit var player: Player

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        player = server.addPlayer()
    }

    @AfterEach
    fun tearDown() = MockBukkit.unmock()

    /**
     * Fills every slot `addItem` will consider so the next add has nowhere to go.
     *
     * Not just `storageContents` — addItem also reaches the armour and offhand slots, so filling
     * storage alone leaves room and an "inventory full" test quietly passes items through.
     */
    private fun fillInventory() {
        for (slot in 0 until player.inventory.size) {
            player.inventory.setItem(slot, ItemStack(Material.STONE, 64))
        }
        check(player.inventory.firstEmpty() == -1) { "inventory is not actually full" }
    }

    @Test
    fun `an item that fits is given and nothing is dropped`() {
        val result = player.giveOrDrop(ItemStack(Material.DIAMOND, 5))

        assertEquals(1, result.given.size)
        assertEquals(5, result.given.single().amount)
        assertTrue(result.dropped.isEmpty())
        assertTrue(result.fullyDelivered)
    }

    @Test
    fun `an item that does not fit is dropped and not reported as given`() {
        fillInventory()

        val result = player.giveOrDrop(ItemStack(Material.DIAMOND, 5))

        // Reporting the input as `given` put the same item in both lists, so anything logging a
        // delivery claimed a player received items that landed on the floor.
        assertTrue(result.given.isEmpty(), "nothing entered the inventory: ${result.given}")
        assertEquals(5, result.dropped.sumOf { it.amount })
        assertFalse(result.fullyDelivered)
    }

    @Test
    fun `a partly accepted stack splits across given and dropped`() {
        fillInventory()
        // One free slot: 64 of a 100-item delivery fit, the rest cannot.
        player.inventory.setItem(0, null)

        val result = player.giveOrDrop(ItemStack(Material.DIAMOND, 100))

        assertEquals(64, result.given.sumOf { it.amount })
        assertEquals(36, result.dropped.sumOf { it.amount })
    }

    @Test
    fun `the caller's stack is never mutated`() {
        fillInventory()
        val stack = ItemStack(Material.DIAMOND, 5)

        player.giveOrDrop(stack)

        // addItem decrements the stack it is handed; handing it the caller's would silently empty
        // an item the caller still holds a reference to.
        assertEquals(5, stack.amount)
    }

    @Test
    fun `every item is attempted even when an earlier one overflows`() {
        fillInventory()
        player.inventory.setItem(0, null)

        val result = player.giveOrDrop(ItemStack(Material.DIAMOND, 64), ItemStack(Material.EMERALD, 1))

        assertEquals(64, result.given.sumOf { it.amount })
        assertEquals(Material.EMERALD, result.dropped.single().type)
    }

    @Test
    fun `an empty delivery is a no-op rather than a failure`() {
        val result = player.giveOrDrop(emptyList())

        assertTrue(result.given.isEmpty())
        assertTrue(result.fullyDelivered)
    }
}
