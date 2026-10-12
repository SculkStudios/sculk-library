package studio.sculk.paper

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.plugin.Plugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.UnsupportedContextException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * [SculkExecutor] over Paper's region-aware schedulers. Paper implements the same four schedulers
 * Folia does, so one implementation serves Paper, Folia and Canvas; only ownership answers differ.
 *
 * Every task is tracked until it finishes, because the platform cancels a disabled plugin's global
 * and async tasks but offers no way to cancel its region or entity tasks.
 */
@SculkInternal
public class PaperExecutor(private val plugin: Plugin, override val platform: String, public val sectionShift: Int) : SculkExecutor {
    private val live = ConcurrentHashMap.newKeySet<ScheduledTask>()

    @Volatile
    private var shutDown = false

    override fun supports(context: ExecutionContext): Boolean = context != ExecutionContext.Proxy

    override fun owns(context: ExecutionContext): Boolean = when (context) {
        ExecutionContext.Global -> Bukkit.isGlobalTickThread()

        ExecutionContext.Async -> !Bukkit.isPrimaryThread()

        ExecutionContext.Proxy -> false

        is ExecutionContext.Region -> world(context).let { world ->
            Bukkit.isOwnedByCurrentRegion(world, context.section.x shl sectionShift, context.section.z shl sectionShift)
        }

        is ExecutionContext.Entity -> Bukkit.isOwnedByCurrentRegion(entity(context))
    }

    override fun schedule(context: ExecutionContext, delayTicks: Long, work: SculkExecutor.Work): SculkHandle =
        track(context, work) { consumer, retired ->
            val delay = delayTicks.coerceAtLeast(1)
            when (context) {
                ExecutionContext.Global -> Bukkit.getGlobalRegionScheduler().runDelayed(plugin, consumer, delay)

                ExecutionContext.Async -> Bukkit.getAsyncScheduler().runDelayed(plugin, consumer, millis(delayTicks), TimeUnit.MILLISECONDS)

                ExecutionContext.Proxy -> throw UnsupportedContextException(context, platform)

                is ExecutionContext.Region -> Bukkit.getRegionScheduler().runDelayed(
                    plugin,
                    world(context),
                    chunkX(context),
                    chunkZ(context),
                    consumer,
                    delay,
                )

                is ExecutionContext.Entity -> entity(context).scheduler.runDelayed(plugin, consumer, retired, delay)
            }
        }

    override fun scheduleRepeating(context: ExecutionContext, delayTicks: Long, periodTicks: Long, work: SculkExecutor.Work): SculkHandle =
        track(context, work, repeating = true) { consumer, retired ->
            val delay = delayTicks.coerceAtLeast(1)
            val period = periodTicks.coerceAtLeast(1)
            when (context) {
                ExecutionContext.Global -> Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, consumer, delay, period)

                ExecutionContext.Async ->
                    Bukkit.getAsyncScheduler().runAtFixedRate(
                        plugin,
                        consumer,
                        millis(delayTicks),
                        period * SculkExecutor.MILLIS_PER_TICK,
                        TimeUnit.MILLISECONDS,
                    )

                ExecutionContext.Proxy -> throw UnsupportedContextException(context, platform)

                is ExecutionContext.Region ->
                    Bukkit.getRegionScheduler().runAtFixedRate(
                        plugin,
                        world(context),
                        chunkX(context),
                        chunkZ(context),
                        consumer,
                        delay,
                        period,
                    )

                is ExecutionContext.Entity -> entity(context).scheduler.runAtFixedRate(plugin, consumer, retired, delay, period)
            }
        }

    /**
     * Schedules through [submit], keeping the task in [live] until it finishes. A null task means
     * Paper refused because the entity is already gone, so the work is retired at once.
     */
    private fun track(
        context: ExecutionContext,
        work: SculkExecutor.Work,
        repeating: Boolean = false,
        submit: (Consumer<ScheduledTask>, Runnable) -> ScheduledTask?,
    ): SculkHandle {
        check(!shutDown) { "$platform executor for ${plugin.name} has shut down." }
        if (!supports(context)) throw UnsupportedContextException(context, platform)
        val holder = AtomicReference<ScheduledTask?>()
        // A one-shot task can finish on another thread before it is added to `live`. Setting
        // `finished` before removing, and checking it after adding, removes it in every interleaving.
        val finished = AtomicBoolean(false)
        val consumer = Consumer<ScheduledTask> { task ->
            if (!repeating) {
                finished.set(true)
                live.remove(task)
            }
            work.run()
        }
        val retired = Runnable {
            finished.set(true)
            holder.get()?.let(live::remove)
            work.retired()
        }
        val task = submit(consumer, retired)
        if (task == null) {
            work.retired()
            return SculkHandle.NONE
        }
        holder.set(task)
        live += task
        if (finished.get()) live.remove(task)
        return SculkHandle {
            task.cancel()
            live.remove(task)
        }
    }

    /** Cancels every tracked task and refuses new ones. Called once, when the runtime closes. */
    public fun shutdown() {
        shutDown = true
        live.forEach { it.cancel() }
        live.clear()
    }

    /** Tasks scheduled and not yet finished, for diagnostics and leak tests. */
    public val liveTasks: Int get() = live.size

    private fun millis(ticks: Long): Long = ticks.coerceAtLeast(0) * SculkExecutor.MILLIS_PER_TICK

    private fun chunkX(context: ExecutionContext.Region): Int = context.section.x shl sectionShift

    private fun chunkZ(context: ExecutionContext.Region): Int = context.section.z shl sectionShift

    private fun world(context: ExecutionContext.Region): World {
        val key =
            NamespacedKey.fromString(context.world.value) ?: throw IllegalArgumentException("'${context.world}' is not a valid world key.")
        return Bukkit.getWorld(key) ?: throw IllegalStateException("World ${context.world} is not loaded.")
    }

    private fun entity(context: ExecutionContext.Entity) = (context.entity as? PaperEntityRef)?.entity
        ?: throw IllegalArgumentException("${context.entity.description} is not a Paper entity; wrap entities with Entity.ref.")
}
