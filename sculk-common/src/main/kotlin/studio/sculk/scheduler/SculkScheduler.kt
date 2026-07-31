package studio.sculk.scheduler

import org.bukkit.Location
import org.bukkit.entity.Entity
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable

/**
 * Where and when work runs. Every task Sculk schedules goes through this.
 *
 * ### Why the region overloads have no defaults
 *
 * On Paper there is one main thread and the region overloads collapse onto it. On Folia the
 * server is split into regions that tick on **different threads at the same time**, and touching
 * a player from the wrong one is a data race that surfaces as duplicated items and dropped
 * chunks rather than as an exception.
 *
 * So [runSync] with an [Entity] or [Location], and every `owns…` query, are declared abstract.
 * They previously defaulted to the global thread and to `false`, which meant an implementation
 * that had not thought about regions compiled cleanly and raced in production. Now it does not
 * compile.
 *
 * ### Choosing a target
 *
 * | Work touches… | Use |
 * | --- | --- |
 * | a player, mob, or their inventory | [runSync] with the entity |
 * | a block, chunk, or world position | [runSync] with the location |
 * | server-wide state, nothing positional | [runSync] with no target |
 * | a database, HTTP, files — never the Paper API | [runAsync] |
 *
 * ### Tick arguments
 *
 * Implementations must accept `delayTicks <= 0` as "next tick" and floor `periodTicks` to 1.
 * Folia's schedulers throw on non-positive values, and a caller computing a delay from a config
 * value should get a scheduled task rather than an exception.
 *
 * Every method returns a [SculkHandle] that cancels the task.
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
