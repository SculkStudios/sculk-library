package studio.sculk.execution

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bookkeeping every platform executor shares. Each task is claimed exactly once, by running (a
 * one-shot task), by retiring, by its handle or by [shutdown], whichever comes first, so a task
 * never both runs and retires, and work still pending at shutdown is retired rather than dropped:
 * a coroutine waiting on it is cancelled instead of hanging forever.
 *
 * Platforms cancel a disabled plugin's global and async tasks but offer no way to cancel its region
 * or entity tasks, which is why every task is tracked until it finishes.
 */
@SculkInternal
public class TaskTracker(private val description: String) {
    private val live = ConcurrentHashMap.newKeySet<Task>()

    @Volatile
    private var closed = false

    /** Tasks scheduled and not yet finished, for diagnostics and leak tests. */
    public val size: Int get() = live.size

    /** One unit of tracked work. Platforms call [run] from their task and [retire] from their retired callback. */
    @SculkInternal
    public inner class Task internal constructor(private val work: SculkExecutor.Work, private val repeating: Boolean) {
        private val claimed = AtomicBoolean()

        @Volatile
        internal var cancel: (() -> Unit)? = null

        internal fun claim(): Boolean = claimed.compareAndSet(false, true).also { if (it) live.remove(this) }

        public fun run() {
            if (repeating) {
                if (!claimed.get()) work.run()
            } else if (claim()) {
                work.run()
            }
        }

        public fun retire() {
            if (claim()) work.retired()
        }

        internal fun cancelAndRetire() {
            if (!claim()) return
            try {
                cancel?.invoke()
            } finally {
                work.retired()
            }
        }
    }

    /**
     * Tracks [work] and starts it with [start], which returns how to cancel the platform's task, or
     * null when the platform refused it because its entity is already gone; the work is then retired
     * at once. Throws [IllegalStateException] once [shutdown] has run.
     */
    public fun submit(work: SculkExecutor.Work, repeating: Boolean, start: (Task) -> (() -> Unit)?): SculkHandle {
        check(!closed) { "$description has shut down." }
        val task = Task(work, repeating)
        live += task
        val cancel = try {
            start(task)
        } catch (failure: Throwable) {
            live.remove(task)
            throw failure
        }
        if (cancel == null) {
            task.retire()
            return SculkHandle.NONE
        }
        task.cancel = cancel
        // A shutdown that ran while start() was submitting saw no cancel action yet.
        if (closed) task.cancelAndRetire()
        return SculkHandle { if (task.claim()) cancel() }
    }

    /** Refuses new work, cancels every pending task and retires it. Called once, when the runtime closes. */
    public fun shutdown() {
        closed = true
        live.toList().forEach(Task::cancelAndRetire)
    }
}
