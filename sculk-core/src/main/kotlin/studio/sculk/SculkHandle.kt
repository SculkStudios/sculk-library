package studio.sculk

import studio.sculk.annotation.SculkStable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Undoes whatever created it: unregisters a listener, cancels a task, removes an entity.
 *
 * Every handle Sculk returns is idempotent. A lambda handle is not unless it makes itself so; wrap
 * it with [once] when it may be closed from more than one place.
 */
@SculkStable
public fun interface SculkHandle : AutoCloseable {
    override fun close()

    @SculkStable
    public companion object {
        public val NONE: SculkHandle = SculkHandle {}

        /** A handle that runs [action] on its first close only. Thread-safe. */
        public fun once(action: () -> Unit): SculkHandle {
            val closed = AtomicBoolean()
            return SculkHandle { if (closed.compareAndSet(false, true)) action() }
        }

        /** Closes [handles] in reverse order, once; every handle is closed even if an earlier one throws. */
        public fun all(handles: List<SculkHandle>): SculkHandle {
            val snapshot = handles.toList()
            return once { closeAll(snapshot.asReversed()) }
        }
    }
}

/**
 * Closes every resource in order. The first exception is rethrown after all have been closed, with
 * the rest attached as suppressed.
 */
internal fun closeAll(resources: Iterable<AutoCloseable>) {
    var first: Throwable? = null
    for (resource in resources) {
        try {
            resource.close()
        } catch (error: Throwable) {
            val primary = first
            if (primary == null) first = error else primary.addSuppressed(error)
        }
    }
    first?.let { throw it }
}
