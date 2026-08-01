package studio.sculk.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import studio.sculk.annotation.SculkInternal
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.scheduler.FakeScheduler
import studio.sculk.text.SculkMessages

/**
 * The click path is the module's security boundary: anything not explicitly an input slot must be
 * cancelled, and cancelling must happen before a handler can throw.
 */
@OptIn(SculkInternal::class)
class MenuListenerTest {
    private lateinit var server: ServerMock
    private lateinit var registry: MenuRegistry
    private lateinit var listener: MenuListener

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        val scheduler = FakeScheduler()
        registry = MenuRegistry(scheduler, SculkCoroutineScope(scheduler), SculkMessages())
        listener = MenuListener(registry)
    }

    @AfterEach
    fun tearDown() {
        registry.close()
        MockBukkit.unmock()
    }

    private fun menu(interactiveSlot: Int? = null, onClick: (GuiContext.() -> Unit)? = null) = gui("<gray>Menu") {
        size = 27
        item(0) {
            material = Material.DIAMOND
            if (onClick != null) onClick(onClick)
        }
        interactiveSlot?.let { slot ->
            item(slot) {
                material = Material.AIR
                interactive()
            }
        }
    }

    private fun click(player: Player, rawSlot: Int, type: ClickType = ClickType.LEFT) = InventoryClickEvent(
        player.openInventory,
        if (rawSlot < player.openInventory.topInventory.size) InventoryType.SlotType.CONTAINER else InventoryType.SlotType.QUICKBAR,
        rawSlot,
        type,
        InventoryAction.PICKUP_ALL,
    )

    @Test
    fun `a click on a decorated slot is cancelled`() {
        val player = server.addPlayer()
        registry.open(menu(), player)

        val event = click(player, 0)
        listener.onClick(event)

        assertTrue(event.isCancelled)
    }

    @Test
    fun `a click on an input slot is left alone`() {
        val player = server.addPlayer()
        registry.open(menu(interactiveSlot = 22), player)

        val event = click(player, 22)
        listener.onClick(event)

        assertFalse(event.isCancelled, "a slot that asked for input must accept it")
    }

    @Test
    fun `the event is already cancelled when the handler runs`() {
        val player = server.addPlayer()
        var cancelledDuringHandler: Boolean? = null
        registry.open(menu(onClick = { cancelledDuringHandler = event.isCancelled }), player)

        listener.onClick(click(player, 0))

        // Dispatching before cancelling turns any exception in a click handler into an item
        // duplication bug, because the throw skips the cancel.
        assertEquals(true, cancelledDuringHandler)
    }

    @Test
    fun `a handler that throws still leaves the click cancelled`() {
        val player = server.addPlayer()
        registry.open(menu(onClick = { error("handler blew up") }), player)
        val event = click(player, 0)

        runCatching { listener.onClick(event) }

        assertTrue(event.isCancelled)
    }

    @Test
    fun `a shift-click from the player's own inventory is cancelled`() {
        val player = server.addPlayer()
        registry.open(menu(interactiveSlot = 22), player)

        // Raw slots past the top inventory belong to the player; a shift-click there moves items
        // into the menu without ever clicking a menu slot.
        val event = click(player, player.openInventory.topInventory.size + 3, ClickType.SHIFT_LEFT)
        listener.onClick(event)

        assertTrue(event.isCancelled)
    }

    @Test
    fun `a click in an inventory that is not a menu is untouched`() {
        val player = server.addPlayer()
        player.openInventory(server.createInventory(null, 27))

        val event = click(player, 0)
        listener.onClick(event)

        assertFalse(event.isCancelled, "a plain chest must not be affected by the menu listener")
    }

    @Test
    fun `a drag touching one locked slot is refused entirely`() {
        val player = server.addPlayer()
        registry.open(menu(interactiveSlot = 22), player)

        // 22 accepts input, 0 does not. A drag cannot be cancelled per slot, so it goes as a whole.
        val event = drag(player, mapOf(22 to ItemStack(Material.DIRT), 0 to ItemStack(Material.DIRT)))
        listener.onDrag(event)

        assertTrue(event.isCancelled)
    }

    @Test
    fun `a drag confined to input slots is allowed`() {
        val player = server.addPlayer()
        registry.open(menu(interactiveSlot = 22), player)

        val event = drag(player, mapOf(22 to ItemStack(Material.DIRT)))
        listener.onDrag(event)

        assertFalse(event.isCancelled)
    }

    @Test
    fun `closing the inventory forgets the session`() {
        val player = server.addPlayer()
        val session = registry.open(menu(), player)
        val inventory = session.openInventory!!

        listener.onClose(InventoryCloseEvent(player.openInventory))

        assertEquals(0, registry.openCount)
        assertNull(registry.sessionFor(inventory))
    }

    private fun drag(player: Player, newItems: Map<Int, ItemStack>) = InventoryDragEvent(
        player.openInventory,
        null,
        ItemStack(Material.DIRT),
        false,
        newItems,
    )
}
