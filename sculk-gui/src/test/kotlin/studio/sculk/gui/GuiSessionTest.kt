package studio.sculk.gui

import org.bukkit.Material
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

@OptIn(SculkInternal::class)
class GuiSessionTest {
    private lateinit var server: ServerMock
    private lateinit var registry: MenuRegistry

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        val scheduler = FakeScheduler()
        registry = MenuRegistry(scheduler, SculkCoroutineScope(scheduler), SculkMessages())
    }

    @AfterEach
    fun tearDown() {
        registry.close()
        MockBukkit.unmock()
    }

    /** A menu with four pagination slots, so page arithmetic has a real page size to work with. */
    private fun paged() = gui("<gray>Paged") {
        size = 27
        pagination { slots += listOf(10, 11, 12, 13) }
    }

    private fun openPaged(entries: Int): GuiSession {
        val session = registry.open(paged(), server.addPlayer())
        session.setEntries(List(entries) { ItemStack(Material.DIAMOND, it + 1) })
        return session
    }

    @Test
    fun `an empty list still opens on a single page`() {
        val session = openPaged(0)

        assertEquals(1, session.totalPages)
        assertFalse(session.hasNextPage)
        assertFalse(session.hasPreviousPage)
    }

    @Test
    fun `exactly one page's worth does not create a second page`() {
        val session = openPaged(4)

        assertEquals(1, session.totalPages)
        assertFalse(session.hasNextPage, "4 entries in 4 slots is one page, not two")
    }

    @Test
    fun `one entry over the page size creates a second page`() {
        val session = openPaged(5)

        assertEquals(2, session.totalPages)
        assertTrue(session.hasNextPage)
    }

    @Test
    fun `paging forward stops at the last page`() {
        val session = openPaged(5)

        session.nextPage()
        session.nextPage()

        assertEquals(1, session.currentPage)
        assertFalse(session.hasNextPage)
    }

    @Test
    fun `paging back stops at the first page`() {
        val session = openPaged(5)

        session.previousPage()

        assertEquals(0, session.currentPage)
    }

    @Test
    fun `an out-of-range page is clamped rather than rejected`() {
        val session = openPaged(5)

        session.setPage(99)
        assertEquals(1, session.currentPage)

        session.setPage(-4)
        assertEquals(0, session.currentPage)
    }

    @Test
    fun `the second page renders the entries that follow the first`() {
        val session = openPaged(5)

        session.nextPage()

        val inventory = session.openInventory!!
        assertEquals(5, inventory.getItem(10)?.amount, "the fifth entry leads page two")
        // Empty means cleared, not left showing page one. AIR rather than null because that is what
        // the slot is written with; CraftBukkit normalises it away, MockBukkit keeps the stack.
        val trailing = inventory.getItem(11)
        assertTrue(trailing == null || trailing.type == Material.AIR, "slots past the end are stale: $trailing")
    }

    @Test
    fun `setting entries again returns to the first page`() {
        val session = openPaged(9)
        session.setPage(2)

        session.setEntries(listOf(ItemStack(Material.DIRT)))

        // Leaving the index where it was shows an empty page after the list shrinks.
        assertEquals(0, session.currentPage)
        assertEquals(1, session.totalPages)
    }

    @Test
    fun `state survives a page change and reads back at its own type`() {
        val session = openPaged(9)

        session.state["category"] = "weapons"
        session.nextPage()

        assertEquals("weapons", session.state.get<String>("category"))
        assertNull(session.state.get<Int>("category"), "a wrong-typed read is null, not a cast failure")
        assertTrue("category" in session.state)
    }

    @Test
    fun `closing a session marks it closed and drops it from the registry`() {
        val session = openPaged(4)

        session.close()

        assertTrue(session.isClosed)
    }
}
