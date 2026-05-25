package studio.sculk.core

import studio.sculk.core.annotation.SculkStable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-owned group of [SculkHandle] instances.
 *
 * Use a task group when a feature starts several tasks/listeners and needs one
 * idempotent cleanup handle. Handles close in reverse registration order so
 * dependent work shuts down before the resources it depends on.
 */
@SculkStable
public class SculkTaskGroup public constructor() : SculkHandle {
    private val closed = AtomicBoolean(false)
    private val handles = CopyOnWriteArrayList<SculkHandle>()

    /**
     * Adds [handle] to this group.
     *
     * If the group is already closed, [handle] is closed immediately.
     */
    @SculkStable
    public fun add(handle: SculkHandle): SculkHandle {
        if (closed.get()) {
            handle.close()
            return handle
        }
        handles += handle
        if (closed.get() && handles.remove(handle)) {
            handle.close()
        }
        return handle
    }

    /** Closes every registered handle once, in reverse registration order. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handles.asReversed().forEach { it.close() }
        handles.clear()
    }
}

/** Builds a [SculkTaskGroup] from [block]. */
@SculkStable
public fun taskGroup(block: SculkTaskGroup.() -> Unit = {}): SculkTaskGroup = SculkTaskGroup().apply(block)
