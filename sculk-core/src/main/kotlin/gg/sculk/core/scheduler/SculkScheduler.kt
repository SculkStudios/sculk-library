package gg.sculk.core.scheduler

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkStable

/**
 * Abstraction over Paper's scheduler for testability and future async support.
 *
 * All scheduling done by Sculk Studio modules goes through this interface.
 * The Paper implementation wraps the Folia-compatible regional scheduler.
 */
@SculkStable
public interface SculkScheduler {
    /**
     * Runs [task] on the main thread on the next available tick.
     */
    public fun runSync(task: Runnable): SculkHandle

    /**
     * Runs [task] on the main thread after [delayTicks].
     */
    public fun runSyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle

    /**
     * Runs [task] on the main thread repeatedly, starting after [delayTicks]
     * and repeating every [periodTicks].
     */
    public fun runSyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle

    /**
     * Runs [task] asynchronously (off the main thread).
     * Never interact with the Paper API from an async task.
     */
    public fun runAsync(task: Runnable): SculkHandle

    /**
     * Runs [task] asynchronously after [delayTicks].
     */
    public fun runAsyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle

    /**
     * Runs [task] asynchronously on a repeating schedule, starting after [delayTicks]
     * and repeating every [periodTicks].
     *
     * Never interact with the Paper API from inside the task.
     * Use for background work: database syncs, HTTP calls, heartbeat tasks.
     *
     * ```kotlin
     * val handle = scheduler.runAsyncRepeating(0L, 20L) {
     *     database.flushPendingWrites()
     * }
     * // Stop later:
     * handle.close()
     * ```
     */
    public fun runAsyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle
}
