package gg.sculk.core

import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.scheduler.SculkScheduler
import java.util.logging.Logger

/**
 * The core runtime provided to every Sculk Studio module.
 *
 * Gives modules access to the scheduler, logger, and handle registration.
 * Registered handles are automatically closed when the runtime is closed.
 */
@SculkStable
public interface SculkRuntime {
    /** The scheduler for running sync and async tasks. */
    public val scheduler: SculkScheduler

    /** The plugin logger. */
    public val logger: Logger

    /**
     * Registers a [SculkHandle] with this runtime.
     *
     * The handle will be closed automatically when [close] is called.
     * Returns the same handle for convenience.
     */
    public fun register(handle: SculkHandle): SculkHandle

    /**
     * Closes all registered handles in reverse registration order.
     * Safe to call multiple times.
     */
    public fun close()
}
