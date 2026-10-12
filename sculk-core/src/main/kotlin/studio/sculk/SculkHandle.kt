package studio.sculk

import studio.sculk.annotation.SculkStable

/** Undoes whatever created it: unregisters a listener, cancels a task, removes an entity. Idempotent. */
@SculkStable
public fun interface SculkHandle : AutoCloseable {
    override fun close()

    @SculkStable
    public companion object {
        public val NONE: SculkHandle = SculkHandle {}

        /** Closes [handles] in reverse order; every handle is closed even if an earlier one throws. */
        public fun all(handles: List<SculkHandle>): SculkHandle = SculkHandle { closeAll(handles.asReversed()) }
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
