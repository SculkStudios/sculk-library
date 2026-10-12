package studio.sculk.paper

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.plugin.Plugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.TaskTracker
import studio.sculk.execution.UnsupportedContextException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.coroutines.EmptyCoroutineContext

/**
 * [SculkExecutor] over Paper's region-aware schedulers. Paper implements the same global, region
 * and entity schedulers Folia does, so one implementation serves Paper, Folia and Canvas; only
 * ownership answers differ.
 *
 * [ExecutionContext.Async] does not use Paper's async scheduler, whose pool is unbounded: async work
 * runs on at most [ASYNC_PARALLELISM] threads per plugin, borrowed from the coroutine IO pool, with
 * one timer thread for delays.
 */
@SculkInternal
public class PaperExecutor(private val plugin: Plugin, override val platform: String, override val regionShift: Int) : SculkExecutor {
    private val tasks = TaskTracker("$platform executor for ${plugin.name}")
    private val async: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(ASYNC_PARALLELISM, "sculk-${plugin.name}-async")
    private val timer = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "sculk-${plugin.name}-timer").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    override fun supports(context: ExecutionContext): Boolean = context != ExecutionContext.Proxy

    override fun owns(context: ExecutionContext): Boolean = when (context) {
        ExecutionContext.Global -> Bukkit.isGlobalTickThread()

        ExecutionContext.Async -> !Bukkit.isPrimaryThread() && !Bukkit.isGlobalTickThread()

        ExecutionContext.Proxy -> false

        // An unloaded world has no owner; answering false sends work to the scheduler, which refuses
        // it, and the dispatcher cancels the coroutine instead of throwing from isDispatchNeeded.
        is ExecutionContext.Region -> world(context)?.let { Bukkit.isOwnedByCurrentRegion(it, context.chunkX, context.chunkZ) } ?: false

        // Paper's main thread "owns" a removed entity too; checking validity on the owner keeps
        // withEntity returning Gone for it rather than running against a dead entity.
        is ExecutionContext.Entity -> entity(context).let { Bukkit.isOwnedByCurrentRegion(it) && it.isValid }
    }

    override fun schedule(context: ExecutionContext, delayTicks: Long, work: SculkExecutor.Work): SculkHandle {
        accept(context)
        val delay = delayTicks.coerceAtLeast(1)
        return tasks.submit(work, repeating = false) { task ->
            val consumer = Consumer<ScheduledTask> { task.run() }
            when (context) {
                ExecutionContext.Global -> Bukkit.getGlobalRegionScheduler().runDelayed(plugin, consumer, delay).canceller()

                ExecutionContext.Async -> {
                    val future = timer.schedule({
                        async.dispatch(EmptyCoroutineContext, Runnable(task::run))
                    }, millis(delay), TimeUnit.MILLISECONDS)
                    return@submit { future.cancel(false) }
                }

                ExecutionContext.Proxy -> throw UnsupportedContextException(context, platform)

                is ExecutionContext.Region ->
                    Bukkit.getRegionScheduler().runDelayed(
                        plugin,
                        loadedWorld(context),
                        context.chunkX,
                        context.chunkZ,
                        consumer,
                        delay,
                    ).canceller()

                is ExecutionContext.Entity -> entity(
                    context,
                ).scheduler.runDelayed(plugin, consumer, Runnable(task::retire), delay)?.canceller()
            }
        }
    }

    override fun scheduleRepeating(context: ExecutionContext, delayTicks: Long, periodTicks: Long, work: SculkExecutor.Work): SculkHandle {
        accept(context)
        val delay = delayTicks.coerceAtLeast(1)
        val period = periodTicks.coerceAtLeast(1)
        return tasks.submit(work, repeating = true) { task ->
            val consumer = Consumer<ScheduledTask> { task.run() }
            when (context) {
                ExecutionContext.Global -> Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, consumer, delay, period).canceller()

                ExecutionContext.Async -> {
                    val future = timer.scheduleAtFixedRate(
                        { async.dispatch(EmptyCoroutineContext, Runnable(task::run)) },
                        millis(delay),
                        millis(period),
                        TimeUnit.MILLISECONDS,
                    )
                    return@submit { future.cancel(false) }
                }

                ExecutionContext.Proxy -> throw UnsupportedContextException(context, platform)

                is ExecutionContext.Region ->
                    Bukkit.getRegionScheduler()
                        .runAtFixedRate(plugin, loadedWorld(context), context.chunkX, context.chunkZ, consumer, delay, period)
                        .canceller()

                is ExecutionContext.Entity ->
                    entity(context).scheduler.runAtFixedRate(plugin, consumer, Runnable(task::retire), delay, period)?.canceller()
            }
        }
    }

    /** Retires every pending task and refuses new ones. Called once, when the runtime closes. */
    public fun shutdown() {
        tasks.shutdown()
        timer.shutdownNow()
    }

    /** Tasks scheduled and not yet finished, for diagnostics and leak tests. */
    public val liveTasks: Int get() = tasks.size

    private fun accept(context: ExecutionContext) {
        if (!supports(context)) throw UnsupportedContextException(context, platform)
    }

    private fun ScheduledTask.canceller(): () -> Unit = { cancel() }

    private fun millis(ticks: Long): Long = ticks * SculkExecutor.MILLIS_PER_TICK

    private fun world(context: ExecutionContext.Region): World? = NamespacedKey.fromString(context.world.value)?.let(Bukkit::getWorld)

    private fun loadedWorld(context: ExecutionContext.Region): World = world(context)
        ?: throw IllegalStateException(
            "World ${context.world} is not loaded; no region owns chunk ${context.chunkX}, ${context.chunkZ}.",
        )

    private fun entity(context: ExecutionContext.Entity) = (context.entity as? PaperEntityRef)?.entity
        ?: throw IllegalArgumentException("${context.entity.description} is not a Paper entity; wrap entities with Entity.ref.")

    @SculkInternal
    public companion object {
        /** Enough for blocking database and HTTP work to overlap, few enough that one plugin cannot exhaust the IO pool. */
        public const val ASYNC_PARALLELISM: Int = 16
    }
}
