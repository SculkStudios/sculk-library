package studio.sculk.lifecycle

import studio.sculk.annotation.SculkStable
import studio.sculk.closeAll

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
    ;

    /** True for the phases a runtime passes through while it starts. */
    public val isStartup: Boolean get() = this < STOPPING
}

/**
 * Advances through [LifecyclePhase] in order and runs the actions registered for each phase.
 *
 * Phases may be skipped (a Bukkit-style plugin never sees [LifecyclePhase.BOOTSTRAP]) but never
 * revisited. Advancing runs the actions of every phase passed, phase by phase, each phase in
 * registration order. Entering [LifecyclePhase.STOPPING] discards startup actions that never ran:
 * a plugin that failed during LOAD must not register commands while it shuts down.
 *
 * Thread-safe: on Folia the global region thread enters READY while the enabling thread may still
 * be registering. Actions run on the thread that advances, outside the lock.
 */
@SculkStable
public class Lifecycle(initial: LifecyclePhase? = null) {
    private val lock = Any()
    private var current: LifecyclePhase? = initial
    private val actions = sortedMapOf<LifecyclePhase, MutableList<() -> Unit>>()

    public val phase: LifecyclePhase? get() = synchronized(lock) { current }

    /** Runs [action] when [target] is entered. Registering for a phase already entered is a wiring bug. */
    public fun on(target: LifecyclePhase, action: () -> Unit) {
        synchronized(lock) {
            val now = current
            check(now == null || target > now) { "Cannot run an action at $target: the runtime is already at $now." }
            actions.getOrPut(target) { mutableListOf() } += action
        }
    }

    /**
     * Enters [target]. Every due action runs even when one throws; the first failure is rethrown
     * afterwards with the rest suppressed. Going backwards is a wiring bug and throws.
     */
    public fun advance(target: LifecyclePhase) {
        val due = synchronized(lock) {
            val now = current
            check(now == null || target > now) { "Cannot move from $now back to $target." }
            current = target
            val passed = actions.keys.filter { it <= target }
            val runs = passed.filter { target.isStartup || !it.isStartup }.flatMap(actions::getValue)
            passed.forEach(actions::remove)
            runs
        }
        closeAll(due.map { action -> AutoCloseable { action() } })
    }

    public fun hasReached(target: LifecyclePhase): Boolean = phase?.let { it >= target } ?: false
}
