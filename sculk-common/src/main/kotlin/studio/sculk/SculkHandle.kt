package studio.sculk

import studio.sculk.annotation.SculkStable

/**
 * An undoable registration — a scheduled task, an event listener, an open menu.
 *
 * Anything Sculk registers on a caller's behalf hands back one of these, so a plugin can undo a
 * single registration without tracking what kind of thing it was.
 *
 * Implementations must tolerate being closed twice. Teardown paths overlap: a plugin closes a
 * handle it kept, and the platform closes the same handle again on disable.
 */
@SculkStable
public fun interface SculkHandle : AutoCloseable {
    override fun close()

    public companion object {
        /** A handle over nothing. Returned when work ran inline and there is nothing to cancel. */
        @SculkStable
        public val NONE: SculkHandle = SculkHandle {}

        /**
         * One handle that closes all of [handles] in reverse registration order.
         *
         * Reverse order because registration order is dependency order: whatever was registered
         * last is most likely to be holding a reference to something registered earlier.
         *
         * Every handle is closed even when one throws. The first failure is rethrown with the
         * rest attached as suppressed, so a single bad `close()` cannot strand the resources
         * behind it — which is how the previous task-group implementation leaked on shutdown.
         */
        @SculkStable
        public fun all(handles: List<SculkHandle>): SculkHandle = SculkHandle {
            var failure: Exception? = null
            for (index in handles.indices.reversed()) {
                try {
                    handles[index].close()
                } catch (error: Exception) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }

        @SculkStable
        public fun all(vararg handles: SculkHandle): SculkHandle = all(handles.asList())
    }
}
