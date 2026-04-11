package gg.sculk.core.gui

import gg.sculk.core.adventure.parseMessage
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * An immutable GUI definition.
 *
 * Defines the layout, items, and click handlers of a chest inventory.
 * Actual per-player state lives in a [GuiSession], created when [openFor] is called.
 *
 * Example:
 * ```kotlin
 * val menu = gui("My Menu") {
 *     size = 27
 *     item(13) {
 *         material = Material.DIAMOND
 *         name = "<aqua>Click Me"
 *         onClick { reply("Clicked!") }
 *     }
 * }
 *
 * menu.openFor(player)
 * ```
 */
@SculkStable
public class Gui
    @SculkInternal
    constructor(
        public val title: String,
        public val size: Int,
        public val items: Map<Int, GuiItem>,
    ) {
        /**
         * Opens this GUI for [player], creating a new [GuiSession].
         *
         * The session is automatically registered with the active [gg.sculk.platform.SculkPlatform]
         * and cleaned up when the player closes the inventory or disconnects.
         */
        public fun openFor(player: Player): GuiSession {
            val session = GuiSession(player, this)
            val inventory = buildInventory()
            GuiRegistry.register(player, session, inventory)
            player.openInventory(inventory)
            return session
        }

        @SculkInternal
        public fun buildInventory(): Inventory {
            val inv = Bukkit.createInventory(null, size, parseMessage(title))
            for ((slot, item) in items) {
                inv.setItem(slot, item.stack)
            }
            return inv
        }
    }

/**
 * DSL builder for a [Gui].
 *
 * ```kotlin
 * gui("Inventory") {
 *     size = 54
 *     item(4) {
 *         material = Material.NETHER_STAR
 *         name = "<gold>Settings"
 *         onClick { open(settingsMenu) }
 *     }
 * }
 * ```
 */
@SculkStable
public class GuiBuilder
    @SculkInternal
    constructor(
        private val title: String,
    ) {
        /** The number of slots. Must be a multiple of 9 between 9 and 54. Defaults to 27. */
        public var size: Int = 27

        private val items: MutableMap<Int, GuiItem> = mutableMapOf()
        private var paginationConfig: PaginationConfig? = null

        /**
         * Defines an item at the given [slot].
         *
         * Slots are 0-indexed from top-left to bottom-right.
         */
        public fun item(
            slot: Int,
            block: GuiItemBuilder.() -> Unit,
        ) {
            require(slot in 0 until size) { "Slot $slot is out of range for a GUI of size $size." }
            @OptIn(SculkInternal::class)
            items[slot] = GuiItemBuilder(slot).apply(block).build()
        }

        /**
         * Configures pagination for this GUI.
         *
         * When pagination is enabled, items added via [PaginationBuilder.entries] are
         * distributed across multiple pages using the configured [PaginationConfig.slots].
         */
        public fun pagination(block: PaginationBuilder.() -> Unit) {
            paginationConfig = PaginationBuilder().apply(block).build()
        }

        @SculkInternal
        public fun build(): Gui {
            require(size % 9 == 0 && size in 9..54) {
                "GUI size must be a multiple of 9 between 9 and 54, got $size."
            }
            return Gui(title, size, items.toMap())
        }
    }

/**
 * Creates a [Gui] with [title].
 *
 * ```kotlin
 * val menu = gui("Main Menu") {
 *     size = 27
 *     item(13) { material = Material.DIAMOND; name = "<aqua>Diamonds!" }
 * }
 * ```
 */
@SculkStable
public fun gui(
    title: String,
    block: GuiBuilder.() -> Unit,
): Gui = GuiBuilder(title).apply(block).build()

// ---------------------------------------------------------------------------
// Pagination stub (expanded in sculk-platform once lifecycle is wired)
// ---------------------------------------------------------------------------

@SculkStable
public class PaginationConfig(
    public val slots: List<Int>,
)

@SculkStable
public class PaginationBuilder {
    /** The slots that paginated items occupy. */
    public val slots: MutableList<Int> = mutableListOf()

    @SculkInternal
    public fun build(): PaginationConfig = PaginationConfig(slots.toList())
}
