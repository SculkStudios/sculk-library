package gg.sculk.core.gui

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.entity.Player

/**
 * Per-player mutable session for an open [Gui].
 *
 * Created when a player opens a GUI via [Gui.openFor].
 * Automatically cleaned up when the player closes the inventory or disconnects.
 *
 * Holds the current [state] and current page index for paginated GUIs.
 */
@SculkStable
public class GuiSession
    @SculkInternal
    constructor(
        /** The player this session belongs to. */
        public val player: Player,
        /** The GUI definition this session is displaying. */
        public val gui: Gui,
    ) {
        /** Mutable state bag for this session. */
        public val state: GuiState = GuiState()

        /** Current page index for paginated GUIs. 0-indexed. */
        public var currentPage: Int = 0
            internal set

        private var closed = false
        private var pendingGuiSwitch: Gui? = null

        /** Closes the inventory for this player. */
        public fun close() {
            if (!closed) {
                closed = true
                player.closeInventory()
            }
        }

        /** Switches to a different [gui] for this player. */
        public fun openGui(gui: Gui) {
            pendingGuiSwitch = gui
            player.closeInventory()
        }

        /** Returns the GUI to open after this session closes, if any. */
        @SculkInternal
        public fun pendingSwitch(): Gui? = pendingGuiSwitch

        /** Advances to the next page if one exists. */
        public fun nextPage() {
            currentPage++
        }

        /** Goes back to the previous page if not on the first page. */
        public fun previousPage() {
            if (currentPage > 0) currentPage--
        }

        /** Whether this session has been closed. */
        public val isClosed: Boolean get() = closed
    }
