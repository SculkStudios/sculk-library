package studio.sculk.event

import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages event listener registrations on behalf of a plugin.
 *
 * All listeners registered through this bus are automatically unregistered
 * when [close] is called — no manual cleanup needed.
 *
 * Example:
 * ```kotlin
 * sculk.events.listen<PlayerJoinEvent> { event ->
 *     event.player.reply("<green>Welcome, ${event.player.name}!")
 * }
 * ```
 */
@SculkStable
public class SculkEventBus(
    @PublishedApi internal val plugin: Plugin,
) : SculkHandle {
    @PublishedApi
    internal val listeners: MutableList<Listener> = mutableListOf()

    /**
     * Registers a listener for event type [T].
     *
     * The handler runs at [NORMAL][EventPriority.NORMAL] priority by default.
     * Returns a [SculkHandle] that unregisters this specific listener when closed.
     */
    @SculkStable
    public inline fun <reified T : Event> listen(
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        noinline filter: (T) -> Boolean = { true },
        crossinline handler: (T) -> Unit,
    ): SculkHandle {
        val listener = object : Listener {}
        plugin.server.pluginManager.registerEvent(
            T::class.java,
            listener,
            priority,
            { _, event -> if (event is T && filter(event)) handler(event) },
            plugin,
            ignoreCancelled,
        )
        listeners += listener
        val closed = AtomicBoolean(false)
        return SculkHandle {
            if (closed.compareAndSet(false, true)) {
                HandlerList.unregisterAll(listener)
                listeners -= listener
            }
        }
    }

    /** Registers a listener that unregisters itself after the first matching event. */
    @SculkStable
    public inline fun <reified T : Event> once(
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        noinline filter: (T) -> Boolean = { true },
        crossinline handler: (T) -> Unit,
    ): SculkHandle {
        var handle: SculkHandle? = null
        handle =
            listen<T>(priority, ignoreCancelled, filter) {
                handler(it)
                handle?.close()
            }
        return handle
    }

    /** Unregisters all listeners registered through this bus. */
    override fun close() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
    }
}
