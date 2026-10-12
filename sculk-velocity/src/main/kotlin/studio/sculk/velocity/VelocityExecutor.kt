package studio.sculk.velocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.SectionPosition
import studio.sculk.execution.TaskTracker
import studio.sculk.execution.UnsupportedContextException
import java.util.concurrent.TimeUnit

/**
 * [SculkExecutor] over Velocity's plugin executor and scheduler. A proxy has no ticks, regions or
 * entities: [ExecutionContext.Proxy] and [ExecutionContext.Async] run on the plugin's executor or
 * Velocity's scheduler threads, never on a network event loop, and a tick is 50 ms of wall-clock time.
 *
 * Ownership is known only for work this executor started, so code entering from a Velocity event
 * hops once onto the executor. That costs a thread handoff and guarantees an event loop is never
 * mistaken for the plugin executor.
 */
@SculkInternal
public class VelocityExecutor(private val proxy: ProxyServer, private val container: PluginContainer) : SculkExecutor {
    override val platform: String = "Velocity"

    /** A proxy has no regions; the value only satisfies the contract. */
    override val regionShift: Int = SectionPosition.DEFAULT_SHIFT

    private val tasks = TaskTracker("Velocity executor for ${container.description.id}")
    private val running = ThreadLocal.withInitial { false }

    override fun supports(context: ExecutionContext): Boolean = context == ExecutionContext.Proxy || context == ExecutionContext.Async

    override fun owns(context: ExecutionContext): Boolean = supports(context) && running.get()

    override fun schedule(context: ExecutionContext, delayTicks: Long, work: SculkExecutor.Work): SculkHandle {
        accept(context)
        return tasks.submit(work, repeating = false) { task ->
            if (delayTicks <= 0) {
                container.executorService.execute { mark(task) }
                return@submit {}
            }
            val scheduled = proxy.scheduler.buildTask(
                container,
                Runnable {
                    mark(task)
                },
            ).delay(millis(delayTicks), TimeUnit.MILLISECONDS).schedule()
            return@submit { scheduled.cancel() }
        }
    }

    override fun scheduleRepeating(context: ExecutionContext, delayTicks: Long, periodTicks: Long, work: SculkExecutor.Work): SculkHandle {
        accept(context)
        return tasks.submit(work, repeating = true) { task ->
            val scheduled = proxy.scheduler.buildTask(container, Runnable { mark(task) })
                .delay(millis(delayTicks), TimeUnit.MILLISECONDS)
                .repeat(millis(periodTicks.coerceAtLeast(1)), TimeUnit.MILLISECONDS)
                .schedule()
            return@submit { scheduled.cancel() }
        }
    }

    private fun accept(context: ExecutionContext) {
        if (!supports(context)) throw UnsupportedContextException(context, platform)
    }

    private fun mark(task: TaskTracker.Task) {
        running.set(true)
        try {
            task.run()
        } finally {
            running.set(false)
        }
    }

    private fun millis(ticks: Long): Long = ticks.coerceAtLeast(0) * SculkExecutor.MILLIS_PER_TICK

    /** Retires every pending task and refuses new ones. Called when the runtime closes. */
    public fun shutdown() {
        tasks.shutdown()
    }

    public val liveTasks: Int get() = tasks.size
}
