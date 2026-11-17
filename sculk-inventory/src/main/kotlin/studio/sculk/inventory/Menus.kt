package studio.sculk.inventory

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.plugin.Plugin
import studio.sculk.ResourceScope
import studio.sculk.annotation.SculkStable
import studio.sculk.item.ItemSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Opens [MenuSpec]s for players and enforces their interaction policy. One per plugin; everything
 * it opened closes with [scope].
 *
 * Thread-safe. A session belongs to its player: on Folia and Canvas it draws, takes clicks and runs
 * handlers on the player's region thread, and calls from any other thread hop there.
 */
@SculkStable
public class Menus(internal val plugin: Plugin, scope: ResourceScope) {
    private val sessions = ConcurrentHashMap.newKeySet<MenuSession<*>>()
    private val listener = MenuListener()

    // Volatile with the concurrent set: a session registering while the scope closes either is seen by
    // the close or sees shutDown itself, so no menu outlives its scope.
    @Volatile
    private var shutDown = false

    init {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        scope.onClose {
            shutDown = true
            sessions.toList().forEach { it.closeAtShutdown() }
            HandlerList.unregisterAll(listener)
        }
    }

    /** The open sessions, for diagnostics. */
    public val open: Collection<MenuSession<*>> get() = sessions.toList()

    /** Opens [menu] at [state] for [player], from any thread; it shows on the player's next tick at the latest. */
    public fun <S : Any> open(player: Player, menu: MenuSpec<S>, state: S): MenuSession<S> {
        val session = MenuSession(this, player, menu, state)
        session.onPlayer(later = false) { session.show() }
        return session
    }

    /** Registers [session]; false once the scope has closed, when the menu must not stay open. */
    internal fun opened(session: MenuSession<*>): Boolean {
        sessions += session
        return !shutDown
    }

    internal fun ended(session: MenuSession<*>) {
        sessions -= session
    }

    private fun Inventory.session(): MenuSession<*>? = (getHolder(false) as? MenuHolder)?.session?.takeIf { it.menus === this@Menus }

    private inner class MenuListener : Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        fun onClick(event: InventoryClickEvent) {
            event.view.topInventory.session()?.click(event)
        }

        @EventHandler(priority = EventPriority.LOWEST)
        fun onDrag(event: InventoryDragEvent) {
            val session = event.view.topInventory.session() ?: return
            if (InteractionPolicy.drag(event.rawSlots, session.size, session.allowsPlayerInventory) ==
                Verdict.CANCEL
            ) {
                event.isCancelled = true
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onClose(event: InventoryCloseEvent) {
            event.inventory.session()?.closed(event.inventory)
        }
    }
}

internal class MenuHolder(val session: MenuSession<*>) : InventoryHolder {
    lateinit var contents: Inventory

    override fun getInventory(): Inventory = contents
}

/**
 * One player's view of a menu. [state] is current as of the last redraw; read and change it on the
 * player's thread, or through [update] from anywhere.
 */
@SculkStable
public class MenuSession<S : Any> internal constructor(
    internal val menus: Menus,
    public val player: Player,
    private val menu: MenuSpec<S>,
    initial: S,
) {
    @Volatile private var current: S = initial

    @Volatile public var isOpen: Boolean = false
        private set

    private var holder: MenuHolder? = null
    private var rendered: Map<Int, ItemSpec> = emptyMap()
    private var handlers: Map<Int, MenuClick<S>.() -> Unit> = emptyMap()
    private var title: String? = null
    private var reopening = false

    public val state: S get() = current

    internal val size: Int get() = menu.layout.size
    internal val allowsPlayerInventory: Boolean get() = menu.allowPlayerInventory

    /** Replaces the state with [transform] of it and redraws what changed, on the player's thread. */
    public fun update(transform: (S) -> S) {
        onPlayer(later = false) {
            if (!isOpen) return@onPlayer
            current = transform(current)
            redraw()
        }
    }

    /** Rebuilds every item, for placeholders whose values changed while the state did not. */
    public fun refresh() {
        onPlayer(later = false) {
            if (!isOpen) return@onPlayer
            rendered = emptyMap()
            redraw()
        }
    }

    /** Closes the menu on the player's next tick. */
    public fun close() {
        onPlayer(later = true) { if (isOpen) player.closeInventory() }
    }

    internal fun <T : Any> openNext(next: MenuSpec<T>, state: T) {
        onPlayer(later = true) { menus.open(player, next, state) }
    }

    internal fun show() {
        val resolver = resolver()
        val newTitle = menu.title(current)
        val holder = MenuHolder(this)
        val component = menu.text.render(newTitle, resolver)
        holder.contents = when (menu.layout.kind) {
            MenuLayout.Kind.CHEST -> Bukkit.createInventory(holder, menu.layout.size, component)
            MenuLayout.Kind.HOPPER -> Bukkit.createInventory(holder, InventoryType.HOPPER, component)
            MenuLayout.Kind.DISPENSER -> Bukkit.createInventory(holder, InventoryType.DISPENSER, component)
        }
        this.holder = holder
        title = newTitle
        rendered = emptyMap()
        draw(menu.draw(current), resolver)
        reopening = isOpen
        // Registered before the window opens: the client can see it before this returns, and a scope
        // closing from another thread in that moment must find the session.
        val first = !isOpen
        isOpen = true
        val live = !first || menus.opened(this)
        val view = try {
            player.openInventory(holder.contents)
        } finally {
            reopening = false
        }
        when {
            view == null && first -> {
                isOpen = false
                menus.ended(this)
            }

            // The scope's listener may already be gone, so the close is ended here as well; closed() runs once.
            !live -> {
                player.closeInventory()
                closed(holder.contents)
            }
        }
    }

    private fun redraw() {
        // Paper cannot retitle an open inventory; a new title is a new view, opened after this tick's click.
        if (menu.title(current) != title) {
            onPlayer(later = true) { if (isOpen) show() }
            return
        }
        draw(menu.draw(current), resolver())
    }

    private fun draw(render: MenuRender<S>, resolver: TagResolver) {
        val inventory = holder?.contents ?: return
        diff(rendered, render.icons).forEach { (slot, item) -> inventory.setItem(slot, item?.create(menu.text, resolver)) }
        rendered = render.icons.toMap()
        handlers = render.handlers.toMap()
    }

    internal fun click(event: InventoryClickEvent) {
        val verdict = InteractionPolicy.click(event.rawSlot, size, event.action, event.click, menu.allowPlayerInventory)
        if (verdict != Verdict.ALLOW) event.isCancelled = true
        if (verdict != Verdict.BUTTON) return
        val handler = handlers[event.rawSlot] ?: return
        try {
            MenuClick(player, current, event.rawSlot, event.click, this).handler()
        } catch (failure: Exception) {
            menus.plugin.logger.log(Level.SEVERE, "A menu handler for slot ${event.rawSlot} failed for ${player.name}", failure)
        }
    }

    internal fun closed(inventory: Inventory) {
        if (inventory !== holder?.contents || reopening || !isOpen) return
        isOpen = false
        menus.ended(this)
        try {
            menu.onClose?.invoke(current, player)
        } catch (failure: Exception) {
            menus.plugin.logger.log(Level.SEVERE, "A menu close handler failed for ${player.name}", failure)
        }
    }

    // Plugins disable on the main thread on Paper; on Folia the player's region may be elsewhere.
    internal fun closeAtShutdown() {
        if (Bukkit.isOwnedByCurrentRegion(player)) player.closeInventory() else close()
    }

    private fun resolver(): TagResolver = menu.placeholders?.invoke(player) ?: TagResolver.empty()

    /** Runs [action] on the player's thread: now when already there and not [later], else on its next tick. */
    internal fun onPlayer(later: Boolean, action: () -> Unit) {
        if (!later && Bukkit.isOwnedByCurrentRegion(player)) {
            action()
        } else {
            player.scheduler.run(menus.plugin, { action() }, null)
        }
    }

    override fun toString(): String = "MenuSession(${player.name}, $current)"
}
