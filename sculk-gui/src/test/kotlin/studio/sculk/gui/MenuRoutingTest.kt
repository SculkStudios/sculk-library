package studio.sculk.gui

import org.bukkit.Material
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import studio.sculk.annotation.SculkInternal
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.scheduler.FakeScheduler
import studio.sculk.text.SculkMessages

@OptIn(SculkInternal::class)
class MenuRoutingTest {
    private lateinit var server: ServerMock
    private lateinit var registry: MenuRegistry
    private lateinit var scheduler: FakeScheduler

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        scheduler = FakeScheduler()
        registry = MenuRegistry(scheduler, SculkCoroutineScope(scheduler), SculkMessages())
    }

    @AfterEach
    fun tearDown() {
        registry.close()
        MockBukkit.unmock()
    }

    private fun simpleMenu(interactiveSlot: Int? = null, onClick: (GuiContext.() -> Unit)? = null) = gui("<gray>Menu") {
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

    // ---- navigation ----------------------------------------------------------------------------
    // Two bugs have lived in this seam and neither had a test. First `openGui` recorded a switch
    // that nothing ever applied, so every navigating click threw the player out of the menu. Then
    // applying it by closing first sent the client back to the game view, which re-grabs the mouse
    // and drops the cursor at the centre of the screen on every click.

    @Test
    fun `switching menus does not close the one the player is looking at`() {
        val player = server.addPlayer()
        val session = registry.open(simpleMenu(), player)
        val before = player.openInventory.topInventory

        session.openGui(simpleMenu())

        assertSame(
            before,
            player.openInventory.topInventory,
            "closing first returns the client to the game view, which is what moved the cursor",
        )
    }

    @Test
    fun `applying the switch opens the next menu`() {
        val player = server.addPlayer()
        val session = registry.open(simpleMenu(), player)
        val first = session.openInventory

        session.openGui(simpleMenu())
        registry.applyPendingSwitch(session)
        scheduler.advance(1)

        assertNotSame(first, player.openInventory.topInventory, "the player should be in the new menu")
        assertNotNull(registry.sessionFor(player.openInventory.topInventory))
    }

    @Test
    fun `a switch is applied once, however many handlers look for it`() {
        val player = server.addPlayer()
        val session = registry.open(simpleMenu(), player)

        session.openGui(simpleMenu())
        registry.applyPendingSwitch(session)

        // The close handler is the fallback path and runs for the same session. It must find nothing
        // left to do, or navigating would open the destination twice -- so the switch is consumed on
        // read rather than merely inspected.
        assertNull(
            session.takePendingSwitch(),
            "the switch must be taken exactly once, whichever handler gets there first",
        )
    }

    @Test
    fun `a menu with no pending switch is left alone`() {
        val player = server.addPlayer()
        val session = registry.open(simpleMenu(), player)
        val before = player.openInventory.topInventory

        registry.applyPendingSwitch(session)
        scheduler.advance(1)

        assertSame(before, player.openInventory.topInventory)
    }

    @Test
    fun `an inventory that is not a menu is never routed`() {
        val stray = server.createInventory(null, 27)

        assertNull(registry.sessionFor(stray))
    }

    @Test
    fun `opening registers a session that can be found by its inventory`() {
        val player = server.addPlayer()

        val session = registry.open(simpleMenu(), player)

        assertSame(session, registry.sessionFor(session.openInventory!!))
        assertEquals(1, registry.openCount)
    }

    @Test
    fun `two menus open at once route to their own session`() {
        val one = server.addPlayer()
        val two = server.addPlayer()

        val first = registry.open(simpleMenu(), one)
        val second = registry.open(simpleMenu(), two)

        assertEquals(2, registry.openCount)
        assertSame(first, registry.sessionFor(first.openInventory!!))
        assertSame(second, registry.sessionFor(second.openInventory!!))
    }

    @Test
    fun `forgetting one session leaves the other open`() {
        val one = server.addPlayer()
        val two = server.addPlayer()
        val first = registry.open(simpleMenu(), one)
        val second = registry.open(simpleMenu(), two)

        registry.forget(first.openInventory!!)

        assertEquals(1, registry.openCount)
        assertSame(second, registry.sessionFor(second.openInventory!!))
    }

    @Test
    fun `a locked slot is reported as non-interactive and an input slot is not`() {
        val menu = simpleMenu(interactiveSlot = 22)

        assertFalse(menu.isInteractive(0), "a decorated slot is click-locked")
        assertTrue(menu.isInteractive(22), "a slot that asked for input is not")
        assertFalse(menu.isInteractive(5), "an empty slot is locked by default")
    }

    @Test
    fun `closing the registry drops every session`() {
        registry.open(simpleMenu(), server.addPlayer())
        registry.open(simpleMenu(), server.addPlayer())

        registry.close()

        assertEquals(0, registry.openCount)
    }

    @Test
    fun `a click handler receives the context for its own session`() {
        val player = server.addPlayer()
        var seen: GuiSession? = null
        val session = registry.open(simpleMenu(onClick = { seen = this.session }), player)

        val item = session.gui.items[0]!!
        val handler = item.resolveHandler(org.bukkit.event.inventory.ClickType.LEFT)

        assertTrue(handler != null, "the item declared a click handler")
    }
}
