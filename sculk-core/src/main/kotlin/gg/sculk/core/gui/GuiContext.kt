package gg.sculk.core.gui

import gg.sculk.core.adventure.actionbar
import gg.sculk.core.adventure.reply
import gg.sculk.core.adventure.title
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent

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

        /** Sends a MiniMessage-formatted [message] to the player. */
        public fun reply(message: String): Unit = player.reply(message)

        /** Sends a title to the player. */
        public fun title(
            title: String,
            subtitle: String = "",
            fadeIn: Int = 10,
            stay: Int = 70,
            fadeOut: Int = 20,
        ) {
            player.title(title, subtitle, fadeIn, stay, fadeOut)
        }

        /** Sends an action bar message to the player. */
        public fun actionbar(message: String): Unit = player.actionbar(message)

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
