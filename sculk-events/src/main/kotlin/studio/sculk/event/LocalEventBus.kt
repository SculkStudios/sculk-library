package studio.sculk.event

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.SculkScope
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * Events a plugin or a Sculk module defines and fires itself. Because the firer is a coroutine that
 * waits, every handler kind is available: [listen] and [await] handlers run one after another in
 * priority order, then [observe] handlers start in the firer's context.
 *
 * A handler registered for a type also receives its subtypes. Handlers belong to [scope] and stop
 * when it closes. Thread-safe.
 */
@SculkStable
public class LocalEventBus(private val scope: SculkScope, private val onFailure: (event: KClass<*>, failure: Throwable) -> Unit) :
    SculkEvents,
    AwaitableEvents {
    private sealed class Handler(val type: KClass<*>, val priority: EventPriority, val includeCancelled: Boolean, val order: Long) {
        class Immediate(type: KClass<*>, priority: EventPriority, includeCancelled: Boolean, order: Long, val run: (Any) -> Unit) :
            Handler(type, priority, includeCancelled, order)

        class Awaited(type: KClass<*>, priority: EventPriority, order: Long, val timeoutMillis: Long, val run: suspend (Any) -> Unit) :
            Handler(type, priority, includeCancelled = false, order)

        class Observer(type: KClass<*>, includeCancelled: Boolean, order: Long, val run: suspend (Any) -> Unit) :
            Handler(type, EventPriority.MONITOR, includeCancelled, order)
    }

    private val handlers = CopyOnWriteArrayList<Handler>()
    private val sequence = AtomicLong()

    override fun <E : Any> listen(type: KClass<E>, priority: EventPriority, includeCancelled: Boolean, handler: (E) -> Unit): SculkHandle {
        @Suppress("UNCHECKED_CAST")
        return add(Handler.Immediate(type, priority, includeCancelled, sequence.getAndIncrement(), handler as (Any) -> Unit))
    }

    override fun <E : Any> observe(type: KClass<E>, includeCancelled: Boolean, handler: suspend (E) -> Unit): SculkHandle {
        @Suppress("UNCHECKED_CAST")
        return add(Handler.Observer(type, includeCancelled, sequence.getAndIncrement(), handler as suspend (Any) -> Unit))
    }

    override fun <E : Any> await(type: KClass<E>, priority: EventPriority, timeoutMillis: Long, handler: suspend (E) -> Unit): SculkHandle {
        require(timeoutMillis > 0) { "An await handler needs a positive timeout, but was $timeoutMillis ms." }
        @Suppress("UNCHECKED_CAST")
        return add(Handler.Awaited(type, priority, sequence.getAndIncrement(), timeoutMillis, handler as suspend (Any) -> Unit))
    }

    private fun add(handler: Handler): SculkHandle {
        handlers += handler
        val registration = SculkHandle.once { handlers -= handler }
        scope.resources.own(registration)
        return registration
    }

    /**
     * Fires [event] and returns it once every deciding handler has run, so the caller can read
     * the outcome, such as [Cancellable.isCancelled]. Observers are started and not waited for.
     */
    public suspend fun <E : Any> fire(event: E): E {
        val matching = handlers.filter { it.type.isInstance(event) }.sortedWith(compareBy(Handler::priority, Handler::order))
        for (handler in matching) {
            if (handler is Handler.Observer || skips(handler, event)) continue
            when (handler) {
                is Handler.Immediate -> report(event) { handler.run(event) }

                is Handler.Awaited -> report(event) {
                    withTimeoutOrNull(handler.timeoutMillis) { handler.run(event) }
                        ?: throw TimeoutException(
                            "A handler for ${event::class.simpleName} did not answer within ${handler.timeoutMillis} ms.",
                        )
                }

                is Handler.Observer -> Unit
            }
        }
        val home = currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext
        matching.filterIsInstance<Handler.Observer>().filterNot { skips(it, event) }.forEach { observer ->
            scope.launch(home) { report(event) { observer.run(event) } }
        }
        return event
    }

    private fun skips(handler: Handler, event: Any): Boolean = !handler.includeCancelled && (event as? Cancellable)?.isCancelled == true

    private inline fun report(event: Any, action: () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onFailure(event::class, failure)
        }
    }
}
