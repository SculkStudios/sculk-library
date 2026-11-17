package studio.sculk.inventory

import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.item.item

/** The parts of a menu that need no server: the interaction policy, the diff and the render helpers. */
class MenuLogicTest {
    private val size = 27
    private val pane = item(Material.GRAY_STAINED_GLASS_PANE)
    private val gem = item(Material.EMERALD)

    @Test
    fun `every click on the menu is refused, and plain clicks reach the slot's handler`() {
        listOf(
            ClickType.LEFT,
            ClickType.RIGHT,
            ClickType.SHIFT_LEFT,
            ClickType.MIDDLE,
            ClickType.NUMBER_KEY,
            ClickType.SWAP_OFFHAND,
            ClickType.DROP,
        )
            .forEach {
                assertEquals(
                    Verdict.BUTTON,
                    InteractionPolicy.click(4, size, InventoryAction.PICKUP_ALL, it, allowPlayerInventory = true),
                    "$it",
                )
            }
        listOf(ClickType.DOUBLE_CLICK, ClickType.CREATIVE, ClickType.WINDOW_BORDER_LEFT)
            .forEach {
                assertEquals(
                    Verdict.CANCEL,
                    InteractionPolicy.click(4, size, InventoryAction.PICKUP_ALL, it, allowPlayerInventory = true),
                    "$it",
                )
            }
    }

    @Test
    fun `nothing reaches the menu from the player's own inventory`() {
        val routes = listOf(
            InventoryAction.MOVE_TO_OTHER_INVENTORY to ClickType.SHIFT_LEFT,
            InventoryAction.COLLECT_TO_CURSOR to ClickType.DOUBLE_CLICK,
            InventoryAction.HOTBAR_SWAP to ClickType.NUMBER_KEY,
            InventoryAction.HOTBAR_SWAP to ClickType.SWAP_OFFHAND,
            InventoryAction.UNKNOWN to ClickType.UNKNOWN,
        )
        routes.forEach { (action, click) ->
            assertEquals(
                Verdict.CANCEL,
                InteractionPolicy.click(size + 5, size, action, click, allowPlayerInventory = true),
                "$action by $click",
            )
        }
    }

    @Test
    fun `the player's own inventory is locked unless the menu allows it`() {
        assertEquals(
            Verdict.CANCEL,
            InteractionPolicy.click(size + 5, size, InventoryAction.PICKUP_ALL, ClickType.LEFT, allowPlayerInventory = false),
        )
        assertEquals(
            Verdict.ALLOW,
            InteractionPolicy.click(size + 5, size, InventoryAction.PICKUP_ALL, ClickType.LEFT, allowPlayerInventory = true),
        )
        assertEquals(
            Verdict.CANCEL,
            InteractionPolicy.click(-999, size, InventoryAction.DROP_ALL_CURSOR, ClickType.LEFT, allowPlayerInventory = false),
        )
    }

    @Test
    fun `a drag touching the menu is refused`() {
        assertEquals(Verdict.CANCEL, InteractionPolicy.drag(setOf(size - 1, size + 1), size, allowPlayerInventory = true))
        assertEquals(Verdict.ALLOW, InteractionPolicy.drag(setOf(size + 1, size + 2), size, allowPlayerInventory = true))
        assertEquals(Verdict.CANCEL, InteractionPolicy.drag(setOf(size + 1), size, allowPlayerInventory = false))
    }

    @Test
    fun `a redraw writes only slots whose item changed, and empties slots no longer drawn`() {
        val before = mapOf(0 to pane, 1 to gem, 2 to pane)
        val after = mapOf(0 to pane, 1 to item(Material.DIAMOND), 3 to gem)
        assertEquals(mapOf(1 to item(Material.DIAMOND), 3 to gem, 2 to null), diff(before, after))
        assertEquals(emptyMap<Int, Any?>(), diff(before, before.toMap()))
    }

    @Test
    fun `pagination shows each page's entries and clamps a page that no longer exists`() {
        val render = MenuRender<Unit>(MenuLayout.chest(3))
        val entries = (1..10).map { item(Material.STONE) { amount = it } }
        val page = render.paginate(slots = listOf(10, 11, 12, 13), entries = entries, page = 7, icon = { it })
        assertEquals(2, page.number)
        assertEquals(3, page.count)
        assertEquals(listOf(9, 10), render.icons.values.map { it.amount })
        assertEquals(setOf(10, 11), render.icons.keys)
    }

    @Test
    fun `a border rings the chest and leaves the middle to the rest of the render`() {
        val render = MenuRender<Unit>(MenuLayout.chest(3))
        render.slot(render.at(1, 4), gem)
        render.border(pane)
        assertEquals(gem, render.icons[13])
        assertEquals(20, render.icons.values.count { it == pane })
        assertEquals((10..16).toSet() - 13, (0 until 27).filter { it !in render.icons }.toSet())
        assertThrows<IllegalArgumentException> { render.at(3, 0) }
    }
}
