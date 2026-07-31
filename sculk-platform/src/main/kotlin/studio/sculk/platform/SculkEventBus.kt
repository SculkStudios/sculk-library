package studio.sculk.platform

import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bukkit event listeners with a handle each.
 *
 * Every listener registered here is unregistered when [close] is called, so a plugin never has to
 * track them itself. Registering one also hands back a handle for undoing just that one.
 *
 * Not a coroutine bus, whatever the old documentation said: handlers run on whatever thread Bukkit
 * fires the event on, which for most events is the one that matters.
 *
 * Example:
 * ```kotlin
 * sculk.events.listen<PlayerJoinEvent> { event ->
 *     event.player.reply("<green>Welcome, ${event.player.name}!")
 * }
 * ```
 */
@SculkStable
public class SculkEventBus(@PublishedApi internal val plugin: Plugin) : SculkHandle {
    @PublishedApi
    // Copy-on-write because registration and handle-close both mutate this, and the second can
    // happen from any thread a plugin closes a handle on.
    internal val listeners: MutableList<Listener> = java.util.concurrent.CopyOnWriteArrayList()

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

    /**
     * Registers a listener for [type], for callers that have a class rather than a reified type.
     */
    @SculkStable
    public fun <T : Event> listen(
        type: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        filter: (T) -> Boolean = { true },
        handler: (T) -> Unit,
    ): SculkHandle {
        val listener = object : Listener {}
        plugin.server.pluginManager.registerEvent(
            type,
            listener,
            priority,
            { _, event ->
                if (type.isInstance(event)) {
                    val typed = type.cast(event)
                    if (filter(typed)) handler(typed)
                }
            },
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

    /** Unregisters all listeners registered through this bus. */
    override fun close() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
    }
}

/**
 * Registers an already-written [Listener] class, returning a handle that unregisters just it.
 *
 * For listeners with several `@EventHandler` methods, where the per-event DSL would mean splitting
 * one cohesive class into unrelated lambdas.
 */
@SculkStable
public fun SculkEventBus.listen(listener: Listener): SculkHandle {
    plugin.server.pluginManager.registerEvents(listener, plugin)
    listeners += listener
    return SculkHandle {
        HandlerList.unregisterAll(listener)
        listeners -= listener
    }
}
