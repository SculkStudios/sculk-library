package studio.sculk.event

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import kotlin.reflect.KClass

/**
 * Event handlers in three kinds, told apart by the API rather than by documentation (ADR-0019):
 *
 * - [listen]: a plain function on the thread firing the event; it may cancel or change the event.
 * - [observe]: a suspending function started after every decision is made, in the event's
 *   execution context; it sees the outcome and cannot change it.
 * - [AwaitableEvents.await]: suspending and able to set the result, offered only where the
 *   platform genuinely waits for the handler.
 *
 * A handler that throws is reported with the event type and never stops the others. Every
 * handler is owned by the scope it was registered from and ends with it.
 */
@SculkStable
public interface SculkEvents {
    /**
     * Runs [handler] synchronously each time [type] fires. Cancelled events are skipped unless
     * [includeCancelled] is set.
     */
    public fun <E : Any> listen(
        type: KClass<E>,
        priority: EventPriority = EventPriority.NORMAL,
        includeCancelled: Boolean = false,
        handler: (E) -> Unit,
    ): SculkHandle

    /**
     * Starts [handler] after [type] has been decided, for work that follows from an event without
     * deciding it: saving, logging, rewards. Skips events that were cancelled unless
     * [includeCancelled] is set.
     */
    public fun <E : Any> observe(type: KClass<E>, includeCancelled: Boolean = false, handler: suspend (E) -> Unit): SculkHandle
}

/** Events whose firer waits for handlers to finish, so a suspending handler may still decide them. */
@SculkStable
public interface AwaitableEvents {
    /**
     * Runs [handler] and waits for it, at most [timeoutMillis]; past that the event continues as
     * if the handler had not answered, and the timeout is reported.
     */
    public fun <E : Any> await(
        type: KClass<E>,
        priority: EventPriority = EventPriority.NORMAL,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        handler: suspend (E) -> Unit,
    ): SculkHandle

    @SculkStable
    public companion object {
        /** Long enough for a database round trip, short enough that a stuck handler cannot hold a login. */
        public const val DEFAULT_TIMEOUT_MILLIS: Long = 5_000
    }
}

/** When a handler runs relative to others for the same event; [MONITOR] handlers must not change it. */
@SculkStable
public enum class EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR,
}

@SculkStable
public inline fun <reified E : Any> SculkEvents.listen(
    priority: EventPriority = EventPriority.NORMAL,
    includeCancelled: Boolean = false,
    noinline handler: (E) -> Unit,
): SculkHandle = listen(E::class, priority, includeCancelled, handler)

@SculkStable
public inline fun <reified E : Any> SculkEvents.observe(
    includeCancelled: Boolean = false,
    noinline handler: suspend (E) -> Unit,
): SculkHandle = observe(E::class, includeCancelled, handler)

@SculkStable
public inline fun <reified E : Any> AwaitableEvents.await(
    priority: EventPriority = EventPriority.NORMAL,
    timeoutMillis: Long = AwaitableEvents.DEFAULT_TIMEOUT_MILLIS,
    noinline handler: suspend (E) -> Unit,
): SculkHandle = await(E::class, priority, timeoutMillis, handler)

/** An event Sculk or a plugin defines and fires itself, which a handler can cancel. */
@SculkStable
public interface Cancellable {
    public var isCancelled: Boolean
}
