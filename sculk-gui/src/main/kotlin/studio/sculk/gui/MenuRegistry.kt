package studio.sculk.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.scheduler.SculkScheduler
import studio.sculk.text.SculkMessages
import java.util.concurrent.ConcurrentHashMap

/**
 * Opens menus and routes inventory events back to them.
 *
 * ### Why this is an instance
 *
 * It used to be an `object` with `@Volatile var plugin/isFolia/scope`, initialised by whichever
 * platform booted. A second plugin using Sculk as a shared library rather than shading it would
 * silently take over the first one's plugin reference and coroutine scope, so the first plugin's
 * menus animated on someone else's scope and stopped when *they* disabled.
 *
 * ### Why it is keyed by inventory
 *
 * Sessions were stored by player UUID while every lookup came from a click event holding an
 * `Inventory`, so routing a click meant a linear scan over every open session on the server. The
 * fix is to key by what the lookup actually has. Looking up by player asks Bukkit which inventory
 * is open and reads the same map, so the two views cannot disagree — a second index keyed by
 * player would drift on quit and on a menu opened from inside a click handler.
 *
 * Opening lives here rather than on [Gui] because a menu cannot be opened safely without a
 * scheduler, a scope and a place to register itself. `Gui.openFor(player)` used to work with none
 * of them, which is how an unregistered, fully lootable inventory could reach a player.
 */
@SculkStable
public class MenuRegistry
@SculkInternal
constructor(
    private val scheduler: SculkScheduler,
    private val scope: SculkCoroutineScope,
    private val messages: SculkMessages,
) : SculkHandle {
    private val sessions = ConcurrentHashMap<Inventory, GuiSession>()

    /** Opens [gui] for [player] and returns the live session. */
    @SculkStable
    public fun open(gui: Gui, player: Player): GuiSession {
        val inventory = gui.buildInventory(messages, player)
        val session = GuiSession(player, gui, scope, messages)

        @OptIn(SculkInternal::class)
        session.openInventory = inventory
        sessions[inventory] = session

        // Registered before the inventory is shown, so a caller may immediately call setEntries()
        // or refresh() — those touch the in-memory Inventory and do not need the entity thread —
        // while the open itself is routed to the thread that owns the player.
        scheduler.runNow(player) {
            player.openInventory(inventory)
            gui.openHandler?.invoke(session)
        }

        @OptIn(SculkInternal::class)
        session.startAnimations()
        return session
    }

    /** The session showing [inventory], or null if it is not one of ours. */
    @SculkInternal
    public fun sessionFor(inventory: Inventory): GuiSession? = sessions[inventory]

    /** The session [player] currently has open, or null. */
    @SculkStable
    public fun sessionFor(player: Player): GuiSession? = sessions[player.openInventory.topInventory]

    /** Forgets [inventory]'s session without closing the player's screen. */
    @SculkInternal
    public fun forget(inventory: Inventory) {
        sessions.remove(inventory)?.let { session ->
            @OptIn(SculkInternal::class)
            val next = session.pendingSwitch()

            @OptIn(SculkInternal::class)
            session.close()

            // The other half of `GuiSession.openGui`, which had none.
            //
            // `openGui` set a pending GUI and closed the inventory to get out of the click handler,
            // and `pendingSwitch()` had **no callers anywhere** -- so every in-menu navigation closed
            // the menu and opened nothing. To a player, clicking any button in any Sculk menu threw
            // them out of the menu, which is indistinguishable from the plugin crashing, and nothing
            // was logged because nothing failed.
            //
            // A tick later, and never sooner: this runs inside `InventoryCloseEvent`, and Bukkit
            // ignores an inventory opened from within one. Scheduled against the player so it is
            // region-correct on Folia.
            if (next != null) {
                scheduler.runSyncDelayed(session.player, 1L) { open(next, session.player) }
            }
        }
    }

    /** How many menus are open. Exposed so a test can assert nothing leaks. */
    @SculkInternal
    public val openCount: Int get() = sessions.size

    /** Closes every open menu. Called on plugin shutdown. */
    override fun close() {
        sessions.values.forEach {
            @OptIn(SculkInternal::class)
            it.close()
        }
        sessions.clear()
    }
}
