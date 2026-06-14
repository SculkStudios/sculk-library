package studio.sculk.tasks

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler

/**
 * Spreads per-element work for a large, changing collection across many ticks.
 *
 * Running one task per element (e.g. one Bukkit task per generator) does not scale, and ticking the
 * whole collection in a single tick can stall the main thread. [BatchTicker] processes at most
 * [batchSize] elements every [periodTicks], advancing a cursor so that — across consecutive runs —
 * every element is visited once per cycle in order, even as [source] grows or shrinks between ticks.
 *
 * [action] runs on the scheduler's sync thread, so it is safe to touch the Bukkit API.
 *
 * ```kotlin
 * val ticker = BatchTicker(sculk.scheduler, source = { activeGenerators.values }, batchSize = 200) { gen ->
 *     gen.tick()
 * }
 * // later:
 * ticker.close()
 * ```
 */
@SculkStable
public class BatchTicker<T>(
    scheduler: SculkScheduler,
    private val source: () -> Collection<T>,
    private val batchSize: Int = 100,
    periodTicks: Long = 1L,
    initialDelayTicks: Long = 1L,
    private val action: (T) -> Unit,
) : SculkHandle {
    init {
        require(batchSize > 0) { "batchSize must be positive, got $batchSize." }
        require(periodTicks > 0) { "periodTicks must be positive, got $periodTicks." }
    }

    private var cursor: Int = 0
    private val handle: SculkHandle = scheduler.runSyncRepeating(initialDelayTicks, periodTicks, ::runBatch)

    /**
     * Processes the next slice. Visible for tests; the scheduler calls this every period.
     *
     * A batch never wraps mid-run: if fewer than [batchSize] elements remain before the end of the
     * snapshot, it processes to the end and resets the cursor, so the next run starts a fresh cycle.
     */
    @studio.sculk.annotation.SculkInternal
    public fun runBatch() {
        val snapshot = source().toList()
        if (snapshot.isEmpty()) {
            cursor = 0
            return
        }
        if (cursor >= snapshot.size) cursor = 0
        val end = minOf(cursor + batchSize, snapshot.size)
        for (i in cursor until end) {
            action(snapshot[i])
        }
        cursor = if (end >= snapshot.size) 0 else end
    }

    /** Stops the ticker. Safe to call multiple times. */
    override fun close(): Unit = handle.close()
}
