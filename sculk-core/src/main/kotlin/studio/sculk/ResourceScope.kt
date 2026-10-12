package studio.sculk

import studio.sculk.annotation.SculkStable

/**
 * Owns resources and closes them together, newest first. Scopes nest: a child is itself a resource
 * of its parent, so closing a plugin scope closes every player and session scope below it.
 *
 * Thread-safe. A resource handed to a closed scope is closed at once and the call throws, because
 * whatever created it is running after shutdown and would otherwise leak.
 */
@SculkStable
public class ResourceScope private constructor(public val name: String, private val parent: ResourceScope?) : SculkHandle {
    private val lock = Any()
    private val resources = LinkedHashSet<AutoCloseable>()
    private val closing = mutableListOf<AutoCloseable>()
    private var closed = false

    public val isClosed: Boolean get() = synchronized(lock) { closed }

    /** Registers [resource] to be closed with this scope and returns it. */
    public fun <T : AutoCloseable> own(resource: T): T {
        val accepted = synchronized(lock) {
            if (!closed) resources.add(resource)
            !closed
        }
        if (!accepted) {
            resource.close()
            throw IllegalStateException("Scope '$name' is closed; the resource was closed instead of registered.")
        }
        return resource
    }

    /** Runs [action] when this scope closes. Closing the returned handle cancels the registration. */
    public fun onClose(action: () -> Unit): SculkHandle {
        val registration = Registration(action)
        own(registration)
        return SculkHandle { release(registration) }
    }

    /**
     * Runs [action] as soon as closing begins, before any owned resource closes, in registration
     * order. For stopping intake first: cancelling coroutines so none runs against a resource that
     * is already closed. On a closed scope [action] runs at once and the call throws.
     */
    public fun onClosing(action: () -> Unit): SculkHandle {
        val registration = Registration(action)
        val accepted = synchronized(lock) {
            if (!closed) closing += registration
            !closed
        }
        if (!accepted) {
            action()
            throw IllegalStateException("Scope '$name' is closed; the action ran at once instead of being registered.")
        }
        return SculkHandle { synchronized(lock) { closing.remove(registration) } }
    }

    /** Stops owning [resource] without closing it. */
    public fun release(resource: AutoCloseable) {
        synchronized(lock) { resources.remove(resource) }
    }

    /** A scope closed with this one, or earlier on its own. */
    public fun child(name: String): ResourceScope = own(ResourceScope("${this.name}/$name", this))

    /**
     * Runs the [onClosing] actions, then closes every owned resource, newest first. Everything runs
     * even when one throws; the first exception is rethrown afterwards with the rest suppressed.
     * Closing twice does nothing.
     */
    override fun close() {
        val snapshot = synchronized(lock) {
            if (closed) return
            closed = true
            (closing.toList() + resources.toList().asReversed()).also {
                closing.clear()
                resources.clear()
            }
        }
        try {
            closeAll(snapshot)
        } finally {
            parent?.release(this)
        }
    }

    override fun toString(): String = "ResourceScope($name${if (isClosed) ", closed" else ""})"

    private class Registration(private val action: () -> Unit) : AutoCloseable {
        override fun close() = action()
    }

    @SculkStable
    public companion object {
        /** A scope with no parent; the caller is responsible for closing it. */
        public fun root(name: String): ResourceScope = ResourceScope(name, null)
    }
}
