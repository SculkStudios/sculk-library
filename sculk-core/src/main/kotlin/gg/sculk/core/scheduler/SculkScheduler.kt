package gg.sculk.core.scheduler

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkStable
import org.bukkit.Location
import org.bukkit.entity.Entity

/**
 * Abstraction over Paper's scheduler for testability and Folia compatibility.
 *
 * All scheduling done by Sculk Studio modules goes through this interface.
 * The platform implementation ([gg.sculk.platform.PaperScheduler]) automatically
 * detects whether the server is running Folia (or a Folia fork such as Canvas) and
 * routes every call to the correct scheduler.
 *
 * ### Thread model
 *
 * - **Paper** — `runSync*` runs on the single main thread; entity/location context is ignored.
 * - **Folia / Canvas** — `runSync` runs on the global region thread; `runSync(entity, …)` runs
 *   on the thread owning the entity's chunk; `runSync(location, …)` runs on the thread owning
 *   that chunk. Use the entity/location overloads whenever the task touches a specific player
 *   or world position.
 */
@SculkStable
public interface SculkScheduler {
    // -------------------------------------------------------------------------
    // Global sync — "main thread" equivalent
    // -------------------------------------------------------------------------

    /** Runs [task] on the main/global-region thread on the next available tick. */
    public fun runSync(task: Runnable): SculkHandle

    /** Runs [task] on the main/global-region thread after [delayTicks]. */
    public fun runSyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle

    /** Runs [task] on the main/global-region thread every [periodTicks], starting after [delayTicks]. */
    public fun runSyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle

    // -------------------------------------------------------------------------
    // Entity-region sync — runs on the thread owning the entity's chunk
    // On Paper, entity context is ignored and falls back to global sync.
    // -------------------------------------------------------------------------

    /**
     * Runs [task] on the thread owning [entity]'s current chunk.
     *
     * On Folia/Canvas this uses the entity scheduler — the only safe way to open inventories,
     * send packets, or modify entity state from async code.
     * On Paper this is identical to [runSync].
     *
     * ```kotlin
     * sculk.scheduler.runAsync {
     *     val result = repo.find(player.uniqueId)
     *     sculk.scheduler.runSync(player) {
     *         // safe to call player API here on both Paper and Folia
     *         player.sendMessage("Coins: ${result.getOrNull()?.coins}")
     *     }
     * }
     * ```
     */
    public fun runSync(
        entity: Entity,
        task: Runnable,
    ): SculkHandle = runSync(task)

    /**
     * Runs [task] on the thread owning [entity]'s chunk after [delayTicks].
     *
     * On Paper this is identical to [runSyncDelayed].
     */
    public fun runSyncDelayed(
        entity: Entity,
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle = runSyncDelayed(delayTicks, task)

    // -------------------------------------------------------------------------
    // Location-region sync — runs on the thread owning the location's chunk
    // On Paper, location context is ignored and falls back to global sync.
    // -------------------------------------------------------------------------

    /**
     * Runs [task] on the thread owning the chunk at [location].
     *
     * On Folia/Canvas this uses the region scheduler — the correct choice for block
     * modifications and location-specific work.
     * On Paper this is identical to [runSync].
     */
    public fun runSync(
        location: Location,
        task: Runnable,
    ): SculkHandle = runSync(task)

    // -------------------------------------------------------------------------
    // Async — off the main/region thread entirely
    // -------------------------------------------------------------------------

    /**
     * Runs [task] asynchronously (off the main thread).
     * Never interact with the Paper API from an async task.
     */
    public fun runAsync(task: Runnable): SculkHandle

    /** Runs [task] asynchronously after [delayTicks]. */
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
