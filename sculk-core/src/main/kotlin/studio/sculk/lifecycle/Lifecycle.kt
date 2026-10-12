package studio.sculk.lifecycle

import studio.sculk.annotation.SculkStable

/** The phases of a Sculk runtime, in order (ADR-0008). */
@SculkStable
public enum class LifecyclePhase {
    /** Paper plugins only: before the server loads. Registry mutation and datapack discovery. */
    BOOTSTRAP,

    /** Configuration is read and validated. */
    CONFIGURE,

    /** Services are created; nothing is registered with the server yet. */
    LOAD,

    /** Commands, listeners and tasks are registered. */
    START,

    /** The server is running and every capability is resolved. */
    READY,

    /** Shutdown has begun; resources are closing. */
    STOPPING,

    /** Everything is closed. */
    STOPPED,
}

/**
 * Advances through [LifecyclePhase] in order and runs the actions registered for each phase.
 * Phases may be skipped (a Bukkit-style plugin starts at [LifecyclePhase.CONFIGURE]) but never
 * revisited.
 *
 * Thread-safe: on Folia the global region thread enters READY while the enabling thread may still
 * be registering. Actions run on the thread that advances, outside the lock.
 */
@SculkStable
public class Lifecycle(initial: LifecyclePhase? = null) {
    private val lock = Any()
    private var current: LifecyclePhase? = initial
    private val actions = LinkedHashMap<LifecyclePhase, MutableList<() -> Unit>>()

    public val phase: LifecyclePhase? get() = synchronized(lock) { current }

    /** Runs [action] when [target] is entered. Registering for a phase already entered is a wiring bug. */
    public fun on(target: LifecyclePhase, action: () -> Unit) {
        synchronized(lock) {
            val now = current
            check(now == null || target > now) { "Cannot run an action at $target: the runtime is already at $now." }
            actions.getOrPut(target) { mutableListOf() } += action
        }
    }

    /** Enters [target], running its actions in registration order. Fails on any attempt to go backwards. */
    public fun advance(target: LifecyclePhase) {
        val due = synchronized(lock) {
            val now = current
            check(now == null || target > now) { "Cannot move from $now back to $target." }
            current = target
            actions.keys.filter { it <= target }.flatMap { actions.remove(it).orEmpty() }
        }
        due.forEach { it() }
    }

    public fun hasReached(target: LifecyclePhase): Boolean = phase?.let { it >= target } ?: false
}
