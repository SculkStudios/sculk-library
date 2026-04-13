package gg.sculk.core.gui

import gg.sculk.core.adventure.parseMessage
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.Bukkit
import org.bukkit.Material
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
        /** Pagination config, non-null when [GuiBuilder.pagination] was called. */
        public val pagination: PaginationConfig? = null,
    ) {
        /**
         * Opens this GUI for [player], creating a new [GuiSession].
         *
         * The session is automatically registered with the active [gg.sculk.platform.SculkPlatform]
         * and cleaned up when the player closes the inventory or disconnects.
         *
         * The returned session can be used to set paginated entries or refresh slots:
         * ```kotlin
         * val session = shopMenu.openFor(player)
         * session.setEntries(shopItems)
         * ```
         */
        public fun openFor(player: Player): GuiSession {
            val session = GuiSession(player, this)
            val inventory = buildInventory(player)
            @OptIn(SculkInternal::class)
            session.openInventory = inventory
            GuiRegistry.register(player, session, inventory)
            player.openInventory(inventory)
            return session
        }

        @SculkInternal
        public fun buildInventory(forPlayer: Player? = null): Inventory {
            val inv = Bukkit.createInventory(null, size, parseMessage(title))
            for ((slot, item) in items) {
                inv.setItem(slot, item.resolveStack(forPlayer))
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
         * Define which slots hold paginated entries. Navigation is wired up manually
         * using `onClick { nextPage() }` / `onClick { previousPage() }` on arrow items.
         *
         * ```kotlin
         * pagination {
         *     slots += (0 until 45).toList()  // top 5 rows
         * }
         * item(45) { material = Material.ARROW; name = "<gray>← Previous"; onClick { previousPage() } }
         * item(53) { material = Material.ARROW; name = "<gray>Next →"; onClick { nextPage() } }
         * ```
         */
        public fun pagination(block: PaginationBuilder.() -> Unit) {
            paginationConfig = PaginationBuilder().apply(block).build()
        }

        /**
         * Fills every slot that has not already been assigned an item.
         *
         * Useful for background filler or glass panes:
         * ```kotlin
         * gui("Menu") {
         *     size = 27
         *     fill(Material.BLACK_STAINED_GLASS_PANE) { name = "<gray> " }
         *     item(13) { material = Material.DIAMOND; name = "<aqua>Click me!" }
         * }
         * ```
         */
        public fun fill(
            material: Material,
            block: GuiItemBuilder.() -> Unit = {},
        ) {
            for (slot in 0 until size) {
                if (slot !in items) {
                    @OptIn(SculkInternal::class)
                    items[slot] = GuiItemBuilder(slot).apply { this.material = material }.apply(block).build()
                }
            }
        }

        /**
         * Fills the outer ring of slots (top row, bottom row, left column, right column)
         * that have not already been assigned an item.
         *
         * ```kotlin
         * gui("Shop") {
         *     size = 54
         *     border(Material.GRAY_STAINED_GLASS_PANE) { name = "<gray> " }
         * }
         * ```
         */
        public fun border(
            material: Material,
            block: GuiItemBuilder.() -> Unit = {},
        ) {
            val rows = size / 9
            for (slot in 0 until size) {
                val row = slot / 9
                val col = slot % 9
                val isBorder = row == 0 || row == rows - 1 || col == 0 || col == 8
                if (isBorder && slot !in items) {
                    @OptIn(SculkInternal::class)
                    items[slot] = GuiItemBuilder(slot).apply { this.material = material }.apply(block).build()
                }
            }
        }

        /**
         * Assigns the same item definition to every slot in [slots].
         *
         * Skips any slot already assigned. Useful for setting an entire row,
         * a column, or an arbitrary set of decoration slots at once:
         * ```kotlin
         * items(listOf(0, 8, 45, 53)) {
         *     material = Material.NETHER_STAR
         *     name = "<gold>★"
         * }
         * ```
         */
        public fun items(
            slots: Iterable<Int>,
            block: GuiItemBuilder.() -> Unit,
        ) {
            for (slot in slots) {
                require(slot in 0 until size) { "Slot $slot is out of range for a GUI of size $size." }
                if (slot !in items) item(slot, block)
            }
        }

        @SculkInternal
        public fun build(): Gui {
            require(size % 9 == 0 && size in 9..54) {
                "GUI size must be a multiple of 9 between 9 and 54, got $size."
            }
            return Gui(title, size, items.toMap(), paginationConfig)
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
// Pagination
// ---------------------------------------------------------------------------

/** Defines which slots are occupied by paginated entries in a [Gui]. */
@SculkStable
public class PaginationConfig(
    public val slots: List<Int>,
)

/** DSL builder for [PaginationConfig]. */
@SculkStable
public class PaginationBuilder {
    /** The slots that paginated entries occupy. */
    public val slots: MutableList<Int> = mutableListOf()

    @SculkInternal
    public fun build(): PaginationConfig = PaginationConfig(slots.toList())
}
