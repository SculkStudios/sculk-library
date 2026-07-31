package studio.sculk.gui

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.text.SculkMessages

/**
 * Context available inside a `onClick { }` handler.
 *
 * Provides access to the player, the click type, the raw event,
 * and convenience helpers for messaging, closing, and paginating the GUI.
 */
@SculkStable
public class GuiContext
@SculkInternal
constructor(
    /** The player who clicked. */
    public val player: Player,
    /** The type of click performed. */
    public val clickType: ClickType,
    /** The raw Bukkit inventory click event. */
    public val event: InventoryClickEvent,
    /** The active session for this player and GUI. Use to read/write state, refresh slots, or update entries. */
    public val session: GuiSession,
) {
    /** The slot that was clicked. */
    public val slot: Int get() = event.slot

    /** The renderer this menu was opened with, so replies pick up the plugin theme. */
    public val messages: SculkMessages get() = session.messages

    /** Renders [template] through the theme and sends it to the player. */
    public fun reply(template: String, vararg values: Pair<String, String>) {
        messages.send(player, template, *values)
    }

    public fun title(title: String, subtitle: String = "", vararg values: Pair<String, String>) {
        messages.title(player, title, subtitle, values = values)
    }

    public fun actionBar(template: String, vararg values: Pair<String, String>) {
        messages.actionBar(player, template, *values)
    }

    /** Closes this GUI for the player. */
    public fun close(): Unit = session.close()

    /** Opens another [gui] for the player, replacing this one. */
    public fun open(gui: Gui): Unit = session.openGui(gui)

    /**
     * Advances to the next page of a paginated GUI and re-renders the pagination slots.
     *
     * No-op when already on the last page.
     *
     * ```kotlin
     * item(53) {
     *     material = Material.ARROW
     *     name = "<gray>Next →"
     *     onClick { nextPage() }
     * }
     * ```
     */
    public fun nextPage(): Unit = session.nextPage()

    /**
     * Goes back to the previous page of a paginated GUI and re-renders the pagination slots.
     *
     * No-op when already on the first page.
     *
     * ```kotlin
     * item(45) {
     *     material = Material.ARROW
     *     name = "<gray>← Previous"
     *     onClick { previousPage() }
     * }
     * ```
     */
    public fun previousPage(): Unit = session.previousPage()
}
