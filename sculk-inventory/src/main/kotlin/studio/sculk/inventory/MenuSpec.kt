package studio.sculk.inventory

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import studio.sculk.annotation.SculkExperimental
import studio.sculk.annotation.SculkStable
import studio.sculk.item.ItemSpec
import studio.sculk.text.Text

/** The shape of a menu's inventory. */
@SculkStable
public class MenuLayout private constructor(internal val kind: Kind, public val size: Int, public val columns: Int) {
    // Paper's InventoryType cannot initialise without a server, so a layout names its own kind.
    internal enum class Kind { CHEST, HOPPER, DISPENSER }

    override fun equals(other: Any?): Boolean = other is MenuLayout && other.kind == kind && other.size == size

    override fun hashCode(): Int = 31 * kind.hashCode() + size

    override fun toString(): String = "MenuLayout($kind, $size)"

    @SculkStable
    public companion object {
        /** A chest of [rows] rows of nine, one to six. */
        public fun chest(rows: Int): MenuLayout {
            require(rows in 1..6) { "a chest menu has 1 to 6 rows, not $rows" }
            return MenuLayout(Kind.CHEST, rows * 9, 9)
        }

        /** A hopper's five slots in a row. */
        public val HOPPER: MenuLayout = MenuLayout(Kind.HOPPER, 5, 5)

        /** A dispenser's three by three. */
        public val DISPENSER: MenuLayout = MenuLayout(Kind.DISPENSER, 9, 3)
    }
}

/** A click on a menu slot, handed to that slot's handler on the player's own thread. */
@SculkStable
public class MenuClick<S : Any> internal constructor(
    public val player: Player,
    public val state: S,
    public val slot: Int,
    public val click: ClickType,
    private val session: MenuSession<S>,
) {
    /** Replaces the state with [transform] of it and redraws what changed. */
    public fun update(transform: (S) -> S) {
        session.update(transform)
    }

    /** Draws the menu again from the same state, rebuilding every item, for when something the render reads outside the state changed. */
    public fun refresh() {
        session.refresh()
    }

    /** Closes the menu after this click, as Paper requires inventories never close inside a click. */
    public fun close() {
        session.close()
    }

    /** Replaces this menu with [menu] at [state] after this click. */
    public fun <T : Any> open(menu: MenuSpec<T>, state: T) {
        session.openNext(menu, state)
    }
}

/** A page of a list, see [MenuRender.paginate]. */
@SculkStable
public class Page(
    /** Which page this is, from zero. */
    public val number: Int,
    /** How many pages there are; at least one, even for an empty list. */
    public val count: Int,
) {
    /** Whether a page comes before this one. */
    public val hasPrevious: Boolean get() = number > 0

    /** Whether a page comes after this one. */
    public val hasNext: Boolean get() = number < count - 1

    override fun toString(): String = "Page(${number + 1} of $count)"
}

/** Marks the menu DSL so a nested block cannot reach an outer builder by accident. */
@SculkStable
@DslMarker
public annotation class MenuDsl

/** One render of a menu: what each slot shows and does for one state. */
@SculkStable
@MenuDsl
public class MenuRender<S : Any> internal constructor(public val layout: MenuLayout) {
    internal val icons = linkedMapOf<Int, ItemSpec>()
    internal val handlers = hashMapOf<Int, MenuClick<S>.() -> Unit>()

    // Set once the session has drawn this render and kept its maps; a handler captured here must not change them.
    internal var drawn = false

    /** The slot at [row] and [column], both from zero. */
    public fun at(row: Int, column: Int): Int {
        require(column in 0 until layout.columns && row in 0 until layout.size / layout.columns) { "($row, $column) is outside $layout" }
        return row * layout.columns + column
    }

    /** Shows [item] in [slot]; [onClick] runs when the player clicks it. */
    public fun slot(slot: Int, item: ItemSpec, onClick: (MenuClick<S>.() -> Unit)? = null) {
        check(!drawn) { "this render is already drawn; change what the menu shows with update or refresh" }
        require(slot in 0 until layout.size) { "slot $slot is outside $layout" }
        icons[slot] = item
        if (onClick != null) handlers[slot] = onClick else handlers.remove(slot)
    }

    /** Shows [item] in every slot nothing else fills, whenever it is called. */
    public fun fill(item: ItemSpec) {
        (0 until layout.size).filter { it !in icons }.forEach { slot(it, item) }
    }

    /** Shows [item] around the edge of a chest menu where nothing else is. */
    public fun border(item: ItemSpec) {
        val rows = layout.size / layout.columns
        (0 until layout.size).filter { slot ->
            val row = slot / layout.columns
            val column = slot % layout.columns
            (row == 0 || row == rows - 1 || column == 0 || column == layout.columns - 1) && slot !in icons
        }.forEach { slot(it, item) }
    }

    /**
     * Shows page [page] of [entries] in [slots], in order, and returns which page that is, clamped
     * to the pages that exist so a state left on a page that no longer exists shows the last one.
     */
    public fun <T> paginate(
        slots: List<Int>,
        entries: List<T>,
        page: Int,
        icon: (T) -> ItemSpec,
        onClick: (MenuClick<S>.(T) -> Unit)? = null,
    ): Page {
        require(slots.isNotEmpty()) { "paginate needs at least one slot" }
        val count = maxOf(1, (entries.size + slots.size - 1) / slots.size)
        val shown = page.coerceIn(0, count - 1)
        val from = shown * slots.size
        entries.subList(from, minOf(from + slots.size, entries.size)).forEachIndexed { index, entry ->
            slot(slots[index], icon(entry), onClick?.let { handler -> { handler(entry) } })
        }
        return Page(shown, count)
    }

    /**
     * Shows [previousIcon] in [previousSlot] while [page] has a page before it and [nextIcon] in
     * [nextSlot] while it has one after; a click runs [turn] with the number of the page to show.
     *
     * ```kotlin
     * pager(page, previousSlot = 45, nextSlot = 53, previousArrow, nextArrow) { number -> update { it.copy(page = number) } }
     * ```
     */
    public fun pager(
        page: Page,
        previousSlot: Int,
        nextSlot: Int,
        previousIcon: ItemSpec,
        nextIcon: ItemSpec,
        turn: MenuClick<S>.(page: Int) -> Unit,
    ) {
        if (page.hasPrevious) slot(previousSlot, previousIcon) { turn(page.number - 1) }
        if (page.hasNext) slot(nextSlot, nextIcon) { turn(page.number + 1) }
    }
}

/**
 * A menu as a function of its state [S]: the title and every slot are drawn from the state, and a
 * click changes the state rather than an item. Each [MenuSession] redraws only the slots whose item
 * spec changed, and a menu is locked: nothing enters or leaves it except through its handlers.
 *
 * ```kotlin
 * data class ShopState(val page: Int = 0)
 *
 * val shop = menu<ShopState>(MenuLayout.chest(6)) {
 *     title("<primary>Shop, page <page>") { state -> mapOf("page" to state.page + 1) }
 *     render { state ->
 *         val page = paginate(slots = offerSlots, entries = offers, page = state.page, icon = { it.icon }) { offer ->
 *             buy(player, offer)
 *         }
 *         pager(page, previousSlot = 45, nextSlot = 53, previousArrow, nextArrow) { number -> update { it.copy(page = number) } }
 *     }
 * }
 * menus.open(player, shop, ShopState())
 * ```
 */
@SculkStable
public class MenuSpec<S : Any> internal constructor(
    public val layout: MenuLayout,
    internal val title: (S) -> MenuTitle,
    internal val render: MenuRender<S>.(S) -> Unit,
    internal val text: Text,
    internal val placeholders: ((Player) -> TagResolver)?,
    /** Whether the player may use their own inventory while the menu is open; it never reaches the menu either way. */
    public val allowPlayerInventory: Boolean,
    internal val onClose: ((S, Player) -> Unit)?,
) {
    internal fun draw(state: S): MenuRender<S> = MenuRender<S>(layout).apply { render(state) }
}

/** Builds a [MenuSpec]; see [menu]. */
@SculkStable
@MenuDsl
public class MenuBuilder<S : Any> internal constructor(private val layout: MenuLayout) {
    private var title: (S) -> MenuTitle = { MenuTitle("") }
    private var render: MenuRender<S>.(S) -> Unit = {}
    private var placeholders: ((Player) -> TagResolver)? = null
    private var onClose: ((S, Player) -> Unit)? = null

    /** Renders titles and item names; set it to use a theme. */
    public var text: Text = Text()

    /** Whether the player may use their own inventory while the menu is open; nothing reaches the menu either way. */
    public var allowPlayerInventory: Boolean = false

    /** A fixed title, a MiniMessage template. */
    public fun title(template: String) {
        title = { MenuTitle(template) }
    }

    /**
     * A title whose [template] stays fixed and takes [values] from the state as placeholders:
     * `title("<primary>Page <page>") { state -> mapOf("page" to state.page + 1) }`. Values are never parsed
     * as markup, so a player's name is safe here. A change of value reopens the menu, as Paper cannot
     * retitle an open inventory.
     */
    @SculkExperimental
    public fun title(template: String, values: (S) -> Map<String, Any?>) {
        title = { state -> MenuTitle(template, values(state)) }
    }

    /**
     * A title template drawn from the state. Prefer the overload with values for anything that is not
     * itself a template: text put into this string is parsed as MiniMessage.
     */
    public fun title(draw: (S) -> String) {
        title = { state -> MenuTitle(draw(state)) }
    }

    /** Draws the slots for a state. Runs on the player's thread each time the state changes. */
    public fun render(draw: MenuRender<S>.(S) -> Unit) {
        render = draw
    }

    /** Placeholders for this player's titles and items, from `sculk-placeholders` or any resolver. */
    public fun placeholders(resolver: (Player) -> TagResolver) {
        placeholders = resolver
    }

    /** Runs when the player closes the menu or leaves, on the player's thread. */
    public fun onClose(action: (state: S, player: Player) -> Unit) {
        onClose = action
    }

    internal fun build(): MenuSpec<S> = MenuSpec(layout, title, render, text, placeholders, allowPlayerInventory, onClose)
}

/** Declares a menu; see [MenuSpec]. */
@SculkStable
public fun <S : Any> menu(layout: MenuLayout, build: MenuBuilder<S>.() -> Unit): MenuSpec<S> = MenuBuilder<S>(layout).apply(build).build()

/** The slots to write to go from [before] to [after]: changed or new items, and null for slots to empty. */
internal fun diff(before: Map<Int, ItemSpec>, after: Map<Int, ItemSpec>): Map<Int, ItemSpec?> = buildMap {
    after.forEach { (slot, item) -> if (before[slot] != item) put(slot, item) }
    for (slot in before.keys) if (slot !in after) put(slot, null)
}

/** A menu title: a template and the values it shows, equal when both are, so a change can be seen. */
internal data class MenuTitle(val template: String, val values: Map<String, Any?> = emptyMap())
