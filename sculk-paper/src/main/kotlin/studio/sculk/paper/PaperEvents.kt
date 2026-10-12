package studio.sculk.paper

import kotlinx.coroutines.CoroutineStart
import org.bukkit.Bukkit
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockEvent
import org.bukkit.event.entity.EntityEvent
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.vehicle.VehicleEvent
import org.bukkit.plugin.EventExecutor
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.event.EventPriority
import studio.sculk.event.SculkEvents
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.WorldKey
import kotlin.reflect.KClass
import org.bukkit.event.EventPriority as BukkitPriority

/**
 * Paper events through Sculk's handler kinds. Registration uses the public
 * `PluginManager.registerEvent`; handlers end when the plugin scope closes.
 *
 * Register concrete event types, such as `PlayerJoinEvent`: Bukkit dispatches by the exact class
 * that owns a handler list and refuses abstract ones. On Folia and Canvas, events fire in parallel
 * across regions, so state shared between handlers must be thread-safe.
 */
@SculkStable
public class PaperEvents internal constructor(private val runtime: PaperRuntime) : SculkEvents {
    override fun <E : Any> listen(type: KClass<E>, priority: EventPriority, includeCancelled: Boolean, handler: (E) -> Unit): SculkHandle =
        register(type, priority.bukkit, includeCancelled) { event -> handler(event) }

    /**
     * Observers run at MONITOR, when every decision is final. Each starts at once on the thread that
     * fired the event, which owns it, and resumes after a suspension in the event's own context:
     * the player's or entity's for their events, the block's region for block events, the global
     * region otherwise.
     */
    override fun <E : Any> observe(type: KClass<E>, includeCancelled: Boolean, handler: suspend (E) -> Unit): SculkHandle =
        register(type, BukkitPriority.MONITOR, includeCancelled) { event ->
            val scope = runtime.scope
            scope.launch(scope.execution.dispatcher(contextOf(event)), CoroutineStart.UNDISPATCHED) {
                report(type) { handler(event) }
            }
        }

    private fun <E : Any> register(type: KClass<E>, priority: BukkitPriority, includeCancelled: Boolean, run: (E) -> Unit): SculkHandle {
        val eventType = bukkitType(type)
        val listener = object : Listener {}
        val executor = EventExecutor { _, event ->
            if (!type.isInstance(event)) return@EventExecutor
            if (!includeCancelled && (event as? Cancellable)?.isCancelled == true) return@EventExecutor
            @Suppress("UNCHECKED_CAST")
            report(type) { run(event as E) }
        }
        Bukkit.getPluginManager().registerEvent(eventType, listener, priority, executor, runtime.plugin, false)
        return runtime.scope.resources.own(SculkHandle.once { HandlerList.unregisterAll(listener) })
    }

    private fun bukkitType(type: KClass<*>): Class<out Event> {
        require(Event::class.java.isAssignableFrom(type.java)) { "${type.simpleName} is not a Bukkit event." }
        return type.java.asSubclass(Event::class.java)
    }

    private inline fun report(type: KClass<*>, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            runtime.log.error("A ${type.simpleName} handler in ${runtime.name} failed.", failure)
        }
    }

    private fun contextOf(event: Any): ExecutionContext = when (event) {
        is PlayerEvent -> ExecutionContext.Entity(event.player.ref)
        is EntityEvent -> ExecutionContext.Entity(event.entity.ref)
        is VehicleEvent -> ExecutionContext.Entity(event.vehicle.ref)
        is InventoryEvent -> ExecutionContext.Entity(event.view.player.ref)
        is BlockEvent -> event.block.let { ExecutionContext.Region.ofBlock(WorldKey(it.world.key.asString()), it.x, it.z) }
        else -> ExecutionContext.Global
    }
}

private val EventPriority.bukkit: BukkitPriority
    get() = when (this) {
        EventPriority.LOWEST -> BukkitPriority.LOWEST
        EventPriority.LOW -> BukkitPriority.LOW
        EventPriority.NORMAL -> BukkitPriority.NORMAL
        EventPriority.HIGH -> BukkitPriority.HIGH
        EventPriority.HIGHEST -> BukkitPriority.HIGHEST
        EventPriority.MONITOR -> BukkitPriority.MONITOR
    }
