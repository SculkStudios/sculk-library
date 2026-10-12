package studio.sculk.velocity

import com.velocitypowered.api.event.EventHandler
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.ResultedEvent
import kotlinx.coroutines.withTimeoutOrNull
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.event.AwaitableEvents
import studio.sculk.event.EventPriority
import studio.sculk.event.SculkEvents
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * Velocity events through Sculk's handler kinds. Velocity waits for an event's handlers before it
 * continues, so [await] handlers may suspend and still decide the outcome, such as denying a login
 * after a database check; the wait is bounded by each handler's timeout.
 *
 * [listen] handlers run on the thread that fired the event, which for some events is a network
 * event loop: they must not block. A resulted event whose result is denied counts as cancelled.
 */
@SculkStable
public class VelocityEvents internal constructor(private val runtime: VelocityRuntime) :
    SculkEvents,
    AwaitableEvents {
    private val plugin: Any get() = runtime.container.instance.orElseThrow {
        IllegalStateException("${runtime.name} has no plugin instance yet.")
    }

    override fun <E : Any> listen(type: KClass<E>, priority: EventPriority, includeCancelled: Boolean, handler: (E) -> Unit): SculkHandle =
        register(type, priority.velocity) { event ->
            if (includeCancelled || !event.isDenied) report(type) { handler(event) }
            null
        }

    /** Observers run after every other handler and are not waited for; they start on the plugin executor. */
    override fun <E : Any> observe(type: KClass<E>, includeCancelled: Boolean, handler: suspend (E) -> Unit): SculkHandle =
        register(type, Short.MIN_VALUE) { event ->
            if (includeCancelled || !event.isDenied) runtime.scope.launch { report(type) { handler(event) } }
            null
        }

    override fun <E : Any> await(type: KClass<E>, priority: EventPriority, timeoutMillis: Long, handler: suspend (E) -> Unit): SculkHandle {
        require(timeoutMillis > 0) { "An await handler needs a positive timeout, but was $timeoutMillis ms." }
        return register(type, priority.velocity) { event ->
            if (event.isDenied) return@register null
            EventTask.withContinuation { continuation ->
                runtime.scope.launch {
                    try {
                        report(type) {
                            withTimeoutOrNull(timeoutMillis) { handler(event) }
                                ?: throw TimeoutException(
                                    "A ${type.simpleName} handler did not answer within $timeoutMillis ms; the event went on without it.",
                                )
                        }
                    } finally {
                        continuation.resume()
                    }
                }
            }
        }
    }

    private fun <E : Any> register(type: KClass<E>, priority: Short, task: (E) -> EventTask?): SculkHandle {
        val handler = object : EventHandler<E> {
            override fun execute(event: E) {
                task(event)
            }

            override fun executeAsync(event: E): EventTask? = task(event)
        }
        runtime.proxy.eventManager.register(plugin, type.java, priority, handler)
        return runtime.scope.resources.own(SculkHandle.once { runtime.proxy.eventManager.unregister(plugin, handler) })
    }

    private inline fun report(type: KClass<*>, action: () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            runtime.log.error("A ${type.simpleName} handler in ${runtime.name} failed.", failure)
        }
    }

    private val Any.isDenied: Boolean get() = (this as? ResultedEvent<*>)?.result?.isAllowed == false
}

/** Velocity calls higher priorities first; Sculk's order, like Bukkit's, runs LOWEST first. */
private val EventPriority.velocity: Short
    get() = when (this) {
        EventPriority.LOWEST -> 2_000
        EventPriority.LOW -> 1_000
        EventPriority.NORMAL -> 0
        EventPriority.HIGH -> -1_000
        EventPriority.HIGHEST -> -2_000
        EventPriority.MONITOR -> -3_000
    }
