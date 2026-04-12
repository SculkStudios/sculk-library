package gg.sculk.core.gui

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Per-player mutable session for an open [Gui].
 *
 * Created when a player opens a GUI via [Gui.openFor].
 * Automatically cleaned up when the player closes the inventory or disconnects.
 *
 * Holds the current [state], current page index, and paginated entries.
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
            private set

        private var closed = false
        private var pendingGuiSwitch: Gui? = null

        private val pageEntries: MutableList<ItemStack> = mutableListOf()

        /** The backing Bukkit inventory — injected by [Gui.openFor] immediately after construction. */
        @SculkInternal
        public var openInventory: Inventory? = null

        /**
         * Sets the entries for a paginated GUI and renders the first page.
         *
         * Entries are distributed across the slots defined in the GUI's [PaginationConfig].
         * Resets to page 0.
         *
         * ```kotlin
         * val session = shopMenu.openFor(player)
         * session.setEntries(shopItems)
         * ```
         */
        @SculkStable
        public fun setEntries(entries: List<ItemStack>) {
            pageEntries.clear()
            pageEntries.addAll(entries)
            currentPage = 0
            refreshPagination()
        }

        /**
         * Re-renders a single [slot] in the open inventory from the current GUI definition.
         *
         * Use this to update a specific slot after changing its underlying data without reopening.
         */
        @SculkStable
        public fun refresh(slot: Int) {
            val inv = openInventory ?: return
            val item = gui.items[slot]
            inv.setItem(slot, item?.stack ?: ItemStack(Material.AIR))
        }

        /**
         * Re-renders all static slots and the current pagination page.
         *
         * Use this to reflect bulk changes to the GUI without reopening.
         */
        @SculkStable
        public fun refreshAll() {
            val inv = openInventory ?: return
            for ((slot, item) in gui.items) {
                inv.setItem(slot, item.stack)
            }
            refreshPagination()
        }

        /**
         * Distributes the current page of [pageEntries] across the pagination slots.
         * Clears any slots beyond the current page's entries.
         */
        private fun refreshPagination() {
            val inv = openInventory ?: return
            val slots = gui.pagination?.slots ?: return
            val startIdx = currentPage * slots.size
            for ((i, slot) in slots.withIndex()) {
                val entryIdx = startIdx + i
                inv.setItem(slot, if (entryIdx < pageEntries.size) pageEntries[entryIdx] else ItemStack(Material.AIR))
            }
        }

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

        /**
         * Advances to the next page and re-renders the pagination slots.
         *
         * Does not wrap — calling `nextPage()` on the last page is a no-op
         * if there are no more entries, but increments the counter otherwise.
         */
        public fun nextPage() {
            val slots = gui.pagination?.slots ?: return
            val totalPages = if (pageEntries.isEmpty()) 1 else (pageEntries.size + slots.size - 1) / slots.size
            if (currentPage < totalPages - 1) {
                currentPage++
                refreshPagination()
            }
        }

        /**
         * Goes back to the previous page and re-renders the pagination slots.
         *
         * No-op when already on the first page.
         */
        public fun previousPage() {
            if (currentPage > 0) {
                currentPage--
                refreshPagination()
            }
        }

        /** Whether this session has been closed. */
        public val isClosed: Boolean get() = closed

        /**
         * Total number of pages for the current entry list.
         * Returns 1 when no pagination is configured or entries are empty.
         */
        public val totalPages: Int
            get() {
                val slots = gui.pagination?.slots ?: return 1
                return if (pageEntries.isEmpty()) 1 else (pageEntries.size + slots.size - 1) / slots.size
            }

        /** True if there is a page after [currentPage]. */
        public val hasNextPage: Boolean get() = currentPage < totalPages - 1

        /** True if there is a page before [currentPage]. */
        public val hasPreviousPage: Boolean get() = currentPage > 0
    }
