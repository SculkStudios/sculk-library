package studio.sculk.velocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import com.velocitypowered.api.scheduler.Scheduler
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.UnsupportedContextException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * [SculkExecutor] over the plugin's own Velocity executor. A proxy has no ticks, regions or
 * entities: [ExecutionContext.Proxy] and [ExecutionContext.Async] both run on the plugin
 * executor, never on a network event loop, and a tick is 50 ms of wall-clock time.
 *
 * Ownership is known only for work this executor started, so code entering from a Velocity event
 * hops once onto the executor. That costs a thread handoff and guarantees an event loop is never
 * mistaken for the plugin executor.
 */
@SculkInternal
public class VelocityExecutor(private val proxy: ProxyServer, private val container: PluginContainer) : SculkExecutor {
    override val platform: String = "Velocity"

    private val live = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val running = ThreadLocal.withInitial { false }

    @Volatile
    private var shutDown = false

    override fun supports(context: ExecutionContext): Boolean = context == ExecutionContext.Proxy || context == ExecutionContext.Async

    override fun owns(context: ExecutionContext): Boolean = supports(context) && running.get()

    override fun schedule(context: ExecutionContext, delayTicks: Long, work: SculkExecutor.Work): SculkHandle {
        accept(context)
        if (delayTicks <= 0) {
            val cancelled = AtomicBoolean(false)
            container.executorService.execute { if (!cancelled.get()) mark(work) }
            return SculkHandle { cancelled.set(true) }
        }
        return track(repeating = false) { finished ->
            val run = Runnable {
                finished()
                mark(work)
            }
            proxy.scheduler.buildTask(container, run).delay(millis(delayTicks), TimeUnit.MILLISECONDS)
        }
    }

    override fun scheduleRepeating(context: ExecutionContext, delayTicks: Long, periodTicks: Long, work: SculkExecutor.Work): SculkHandle {
        accept(context)
        return track(repeating = true) { _ ->
            proxy.scheduler.buildTask(container, Runnable { mark(work) })
                .delay(millis(delayTicks), TimeUnit.MILLISECONDS)
                .repeat(millis(periodTicks.coerceAtLeast(1)), TimeUnit.MILLISECONDS)
        }
    }

    private fun accept(context: ExecutionContext) {
        check(!shutDown) { "Velocity executor for ${container.description.id} has shut down." }
        if (!supports(context)) throw UnsupportedContextException(context, platform)
    }

    private fun track(repeating: Boolean, build: (finished: () -> Unit) -> Scheduler.TaskBuilder): SculkHandle {
        val finished = AtomicBoolean(false)
        val task = AtomicReference<ScheduledTask?>()
        val builder = build {
            if (!repeating) {
                finished.set(true)
                task.get()?.let(live::remove)
            }
        }
        val scheduled = builder.schedule()
        task.set(scheduled)
        live += scheduled
        if (finished.get()) live.remove(scheduled)
        return SculkHandle {
            scheduled.cancel()
            live.remove(scheduled)
        }
    }

    private fun mark(work: SculkExecutor.Work) {
        running.set(true)
        try {
            work.run()
        } finally {
            running.set(false)
        }
    }

    private fun millis(ticks: Long): Long = ticks.coerceAtLeast(0) * SculkExecutor.MILLIS_PER_TICK

    /** Cancels every tracked task and refuses new ones. Called when the runtime closes. */
    public fun shutdown() {
        shutDown = true
        live.forEach { it.cancel() }
        live.clear()
    }

    public val liveTasks: Int get() = live.size
}
