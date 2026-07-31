package studio.sculk.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import studio.sculk.annotation.SculkInternal

/**
 * The one Bukkit listener behind every Sculk menu.
 *
 * Registered unconditionally by the platform. It previously came up only if a plugin remembered to
 * call `gui()` on the platform builder, while `Gui.openFor(player)` worked regardless — so a plugin
 * that skipped the flag got a menu whose clicks were never cancelled, which is to say a chest full
 * of free items.
 *
 * **Cancels first, then dispatches.** A handler that throws must not be able to leave the click
 * applied; doing it the other way round turns any bug in a click handler into an item duplication
 * bug.
 */
@SculkInternal
public class MenuListener(private val registry: MenuRegistry) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public fun onClick(event: InventoryClickEvent) {
        val session = registry.sessionFor(event.view.topInventory) ?: return
        val player = event.whoClicked as? Player ?: return

        val clickedTop = event.clickedInventory === event.view.topInventory
        val interactive = clickedTop && session.gui.isInteractive(event.slot)

        // Everything is cancelled unless the slot explicitly accepts input — including clicks in
        // the player's own inventory, because a shift-click from there moves items into the menu.
        if (!interactive) event.isCancelled = true

        if (!clickedTop) return
        session.handleClick(player, event)
    }

    @EventHandler(priority = EventPriority.HIGH)
    public fun onDrag(event: InventoryDragEvent) {
        val session = registry.sessionFor(event.view.topInventory) ?: return

        // A drag can span both inventories; if any slot it touches belongs to the menu and is not
        // an input slot, the whole drag is refused. Cancelling per-slot is not possible.
        val touchesLocked = event.rawSlots.any { raw ->
            raw < event.view.topInventory.size && !session.gui.isInteractive(raw)
        }
        if (touchesLocked) event.isCancelled = true
    }

    @EventHandler
    public fun onClose(event: InventoryCloseEvent) {
        registry.forget(event.view.topInventory)
    }

    @EventHandler
    public fun onQuit(event: PlayerQuitEvent) {
        // A player who disconnects with a menu open never fires InventoryCloseEvent on every
        // server implementation, and the session would hold their inventory for the uptime.
        registry.sessionFor(event.player)?.let { registry.forget(event.player.openInventory.topInventory) }
    }
}
