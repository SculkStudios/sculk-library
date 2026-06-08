@file:JvmName("SculkGui")

package studio.sculk.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import studio.sculk.adventure.parseMessage
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import java.util.function.Consumer

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
    /** Non-chest container type (hopper, dispenser, …), or null for a chest of [size] slots. */
    public val type: org.bukkit.event.inventory.InventoryType? = null,
    /** Pagination config, non-null when [GuiBuilder.pagination] was called. */
    public val pagination: PaginationConfig? = null,
    /** Called after the inventory is opened for a player. */
    public val openHandler: ((GuiSession) -> Unit)? = null,
    /** Called when the inventory close event is routed for this session. */
    public val closeHandler: ((GuiSession) -> Unit)? = null,
) {
    /**
     * Opens this GUI for [player], creating a new [GuiSession].
     *
     * The session is automatically registered with the active [studio.sculk.platform.SculkPlatform]
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

        // On Folia/Canvas, player.openInventory() must run on the entity's region thread.
        // We set session.openInventory and register BEFORE dispatching so that callers can
        // call session.setEntries() / session.refresh() immediately — those operate on the
        // in-memory Inventory object and don't need the entity thread.
        val plugin = GuiRegistry.plugin
        if (plugin != null && GuiRegistry.isFolia) {
            player.scheduler.run(
                plugin,
                {
                    player.openInventory(inventory)
                    openHandler?.invoke(session)
                },
                null,
            )
        } else {
            player.openInventory(inventory)
            openHandler?.invoke(session)
        }
        @OptIn(SculkInternal::class)
        session.startAnimations()
        return session
    }

    @SculkInternal
    public fun buildInventory(forPlayer: Player? = null): Inventory {
        val inv =
            if (type != null) {
                Bukkit.createInventory(null, type, parseMessage(title))
            } else {
                Bukkit.createInventory(null, size, parseMessage(title))
            }
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
constructor(private val title: String) {
    /** The number of slots. Must be a multiple of 9 between 9 and 54. Defaults to 27. */
    public var size: Int = 27

    /**
     * A non-chest container type (e.g. [org.bukkit.event.inventory.InventoryType.HOPPER] or
     * `DISPENSER`). Setting it adjusts [size] to the type's slot count automatically.
     */
    public var type: org.bukkit.event.inventory.InventoryType? = null
        set(value) {
            field = value
            if (value != null) size = value.defaultSize
        }

    private val items: MutableMap<Int, GuiItem> = mutableMapOf()
    private var paginationConfig: PaginationConfig? = null
    private var openHandler: ((GuiSession) -> Unit)? = null
    private var closeHandler: ((GuiSession) -> Unit)? = null

    /**
     * Defines an item at the given [slot].
     *
     * Slots are 0-indexed from top-left to bottom-right.
     */
    public fun item(slot: Int, block: GuiItemBuilder.() -> Unit) {
        require(slot in 0 until size) { "Slot $slot is out of range for a GUI of size $size." }
        @OptIn(SculkInternal::class)
        items[slot] = GuiItemBuilder(slot).apply(block).build()
    }

    /**
     * Java-friendly overload of [item] taking a [Consumer].
     *
     * ```java
     * b.item(13, i -> { i.setMaterial(Material.DIAMOND); i.setName("<aqua>Click"); i.onClick(c -> c.reply("Hi")); });
     * ```
     */
    @SculkStable
    @OptIn(SculkInternal::class)
    public fun item(slot: Int, block: Consumer<GuiItemBuilder>) {
        require(slot in 0 until size) { "Slot $slot is out of range for a GUI of size $size." }
        items[slot] = GuiItemBuilder(slot).also { block.accept(it) }.build()
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

    /** Java-friendly overload of [pagination] taking a [Consumer]. */
    @SculkStable
    public fun pagination(block: Consumer<PaginationBuilder>) {
        paginationConfig = PaginationBuilder().also { block.accept(it) }.build()
    }

    /** Runs after this GUI is opened for a player. */
    public fun onOpen(handler: (GuiSession) -> Unit) {
        openHandler = handler
    }

    /** Java-friendly overload of [onOpen] taking a [Consumer]. */
    @SculkStable
    public fun onOpen(handler: Consumer<GuiSession>) {
        openHandler = { handler.accept(it) }
    }

    /** Runs when this GUI session is closed through the platform GUI listener. */
    public fun onClose(handler: (GuiSession) -> Unit) {
        closeHandler = handler
    }

    /** Java-friendly overload of [onClose] taking a [Consumer]. */
    @SculkStable
    public fun onClose(handler: Consumer<GuiSession>) {
        closeHandler = { handler.accept(it) }
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
    @JvmOverloads
    public fun fill(material: Material, block: GuiItemBuilder.() -> Unit = {}) {
        for (slot in 0 until size) {
            if (slot !in items) {
                @OptIn(SculkInternal::class)
                items[slot] = GuiItemBuilder(slot).apply { this.material = material }.apply(block).build()
            }
        }
    }

    /** Java-friendly overload of [fill] taking a [Consumer]. */
    @SculkStable
    public fun fill(material: Material, block: Consumer<GuiItemBuilder>): Unit = fill(material) { block.accept(this) }

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
    @JvmOverloads
    public fun border(material: Material, block: GuiItemBuilder.() -> Unit = {}) {
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

    /** Java-friendly overload of [border] taking a [Consumer]. */
    @SculkStable
    public fun border(material: Material, block: Consumer<GuiItemBuilder>): Unit = border(material) { block.accept(this) }

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
    public fun items(slots: Iterable<Int>, block: GuiItemBuilder.() -> Unit) {
        for (slot in slots) {
            require(slot in 0 until size) { "Slot $slot is out of range for a GUI of size $size." }
            if (slot !in items) item(slot, block)
        }
    }

    /** Java-friendly overload of [items] taking a [Consumer]. */
    @SculkStable
    public fun items(slots: Iterable<Int>, block: Consumer<GuiItemBuilder>): Unit = items(slots) { block.accept(this) }

    @SculkInternal
    public fun build(): Gui {
        if (type == null) {
            require(size % 9 == 0 && size in 9..54) {
                "GUI size must be a multiple of 9 between 9 and 54, got $size."
            }
        }
        return Gui(
            title = title,
            size = size,
            items = items.toMap(),
            type = type,
            pagination = paginationConfig,
            openHandler = openHandler,
            closeHandler = closeHandler,
        )
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
public fun gui(title: String, block: GuiBuilder.() -> Unit): Gui = GuiBuilder(title).apply(block).build()

/**
 * Java-friendly overload of [gui] taking a [Consumer].
 *
 * ```java
 * Gui menu = SculkGui.gui("Main Menu", b -> {
 *     b.setSize(27);
 *     b.item(13, i -> { i.setMaterial(Material.DIAMOND); i.setName("<aqua>Diamonds!"); });
 * });
 * ```
 */
@SculkStable
public fun gui(title: String, block: Consumer<GuiBuilder>): Gui = GuiBuilder(title).also { block.accept(it) }.build()

/** Creates a compact confirmation menu with configurable confirm/cancel buttons. */
@SculkStable
public fun confirmMenu(title: String, block: ConfirmMenuBuilder.() -> Unit): Gui = ConfirmMenuBuilder(title).apply(block).build()

/** Java-friendly overload of [confirmMenu] taking a [Consumer]. */
@SculkStable
public fun confirmMenu(title: String, block: Consumer<ConfirmMenuBuilder>): Gui =
    ConfirmMenuBuilder(title).also { block.accept(it) }.build()

/** Builder for [confirmMenu]. */
@SculkStable
public class ConfirmMenuBuilder internal constructor(private val title: String) {
    private var confirmBlock: (GuiItemBuilder.() -> Unit)? = null
    private var cancelBlock: (GuiItemBuilder.() -> Unit)? = null

    /** Configures the confirm button. */
    public fun confirm(block: GuiItemBuilder.() -> Unit) {
        confirmBlock = block
    }

    /** Java-friendly overload of [confirm] taking a [Consumer]. */
    @SculkStable
    public fun confirm(block: Consumer<GuiItemBuilder>) {
        confirmBlock = { block.accept(this) }
    }

    /** Configures the cancel button. */
    public fun cancel(block: GuiItemBuilder.() -> Unit) {
        cancelBlock = block
    }

    /** Java-friendly overload of [cancel] taking a [Consumer]. */
    @SculkStable
    public fun cancel(block: Consumer<GuiItemBuilder>) {
        cancelBlock = { block.accept(this) }
    }

    @SculkInternal
    public fun build(): Gui = gui(title) {
        size = 27
        item(11) {
            material = Material.LIME_CONCRETE
            name = "<green>Confirm"
            confirmBlock?.invoke(this)
        }
        item(15) {
            material = Material.RED_CONCRETE
            name = "<red>Cancel"
            cancelBlock?.invoke(this)
        }
    }
}

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

/** Defines which slots are occupied by paginated entries in a [Gui]. */
@SculkStable
public class PaginationConfig(public val slots: List<Int>)

/** DSL builder for [PaginationConfig]. */
@SculkStable
public class PaginationBuilder {
    /** The slots that paginated entries occupy. */
    public val slots: MutableList<Int> = mutableListOf()

    @SculkInternal
    public fun build(): PaginationConfig = PaginationConfig(slots.toList())
}
