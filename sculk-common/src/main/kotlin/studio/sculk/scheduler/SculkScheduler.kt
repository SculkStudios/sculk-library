package studio.sculk.scheduler

import org.bukkit.Location
import org.bukkit.entity.Entity
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable

/**
 * Where and when work runs. Every task Sculk schedules goes through this, and every method returns
 * a [SculkHandle] that cancels it.
 *
 * The entity and location overloads are abstract deliberately: on Folia, touching a player from
 * the wrong region thread is a data race, so a scheduler that has not thought about regions must
 * fail to compile rather than race. Implementations take `delayTicks <= 0` as "next tick" and
 * floor `periodTicks` to 1.
 *
 * See [docs.sculk.studio/core/scheduler](https://docs.sculk.studio/core/scheduler/).
 */
@SculkStable
public interface SculkScheduler {
    /** Runs [task] on the main/global-region thread on the next tick. */
    @SculkStable
    public fun runSync(task: Runnable): SculkHandle

    @SculkStable
    public fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle

    @SculkStable
    public fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle

    /**
     * Runs [task] on the thread owning [entity]'s chunk.
     *
     * The only safe way to open an inventory, send a packet, or modify entity state for a
     * specific player from code that is not already on that entity's thread.
     */
    @SculkStable
    public fun runSync(entity: Entity, task: Runnable): SculkHandle

    @SculkStable
    public fun runSyncDelayed(entity: Entity, delayTicks: Long, task: Runnable): SculkHandle

    @SculkStable
    public fun runSyncRepeating(entity: Entity, delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle

    /** Runs [task] on the thread owning the chunk at [location]. Use for block and world edits. */
    @SculkStable
    public fun runSync(location: Location, task: Runnable): SculkHandle

    @SculkStable
    public fun runSyncDelayed(location: Location, delayTicks: Long, task: Runnable): SculkHandle

    @SculkStable
    public fun runSyncRepeating(location: Location, delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle

    /** True when the calling thread already owns [entity] and may touch its API directly. */
    @SculkStable
    public fun ownsThread(entity: Entity): Boolean

    /** True when the calling thread already owns the region containing [location]. */
    @SculkStable
    public fun ownsThread(location: Location): Boolean

    /** True when the calling thread is the main/global-region thread. */
    @SculkStable
    public fun ownsGlobalThread(): Boolean

    /**
     * Runs [task] inline when the caller already owns [entity]'s thread, otherwise schedules it.
     *
     * For work already on the right thread that wants to stay in the same tick — pushing
     * per-player packets from a tick loop, where a scheduled task per packet would mean
     * thousands of tasks and a tick of latency to achieve nothing.
     */
    @SculkStable
    public fun runNow(entity: Entity, task: Runnable): SculkHandle = if (ownsThread(entity)) {
        task.run()
        SculkHandle.NONE
    } else {
        runSync(entity, task)
    }

    @SculkStable
    public fun runNow(location: Location, task: Runnable): SculkHandle = if (ownsThread(location)) {
        task.run()
        SculkHandle.NONE
    } else {
        runSync(location, task)
    }

    @SculkStable
    public fun runNow(task: Runnable): SculkHandle = if (ownsGlobalThread()) {
        task.run()
        SculkHandle.NONE
    } else {
        runSync(task)
    }

    /** Runs [task] off the main thread. Never touch the Paper API from inside it. */
    @SculkStable
    public fun runAsync(task: Runnable): SculkHandle

    @SculkStable
    public fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle

    @SculkStable
    public fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle
}
