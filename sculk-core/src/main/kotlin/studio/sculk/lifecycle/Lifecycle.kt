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
 * revisited. Not thread-safe: the platform advances it from one thread.
 */
@SculkStable
public class Lifecycle(initial: LifecyclePhase? = null) {
    public var phase: LifecyclePhase? = initial
        private set

    private val actions = LinkedHashMap<LifecyclePhase, MutableList<() -> Unit>>()

    /** Runs [action] when [target] is entered. Registering for a phase already entered is a wiring bug. */
    public fun on(target: LifecyclePhase, action: () -> Unit) {
        val current = phase
        check(current == null || target > current) { "Cannot run an action at $target: the runtime is already at $current." }
        actions.getOrPut(target) { mutableListOf() } += action
    }

    /** Enters [target], running its actions in registration order. Fails on any attempt to go backwards. */
    public fun advance(target: LifecyclePhase) {
        val current = phase
        check(current == null || target > current) { "Cannot move from $current back to $target." }
        phase = target
        actions.remove(target)?.forEach { it() }
    }

    public fun hasReached(target: LifecyclePhase): Boolean = phase?.let { it >= target } ?: false
}
