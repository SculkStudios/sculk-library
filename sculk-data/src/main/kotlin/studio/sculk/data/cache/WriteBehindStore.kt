package studio.sculk.data.cache

import kotlinx.coroutines.runBlocking
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.data.repository.SculkRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * A write-behind buffer over a [SculkRepository] for hot, frequently-mutated state.
 *
 * Instead of issuing one database write per mutation, callers [markDirty] entities as they change
 * and periodically [flush] the accumulated set in a single batched transaction (via
 * [SculkRepository.saveAll]). Repeated mutations of the same primary key coalesce into one write,
 * so a value that changes many times between flushes is persisted only once.
 *
 * Typical wiring: drive [flush] from an async repeating task, and call [flushBlocking] once more
 * from your plugin's shutdown so nothing is lost.
 *
 * ```kotlin
 * val store = WriteBehindStore(repo) { it.id }
 * // hot path (any thread):
 * store.markDirty(record)
 * // background flush loop:
 * sculk.scheduler.runAsyncRepeating(0L, 100L) { runBlocking { store.flush() } }
 * // shutdown:
 * store.flushBlocking()
 * ```
 *
 * Thread-safe: [markDirty] and [remove] may be called from any thread; [flush] drains a snapshot so
 * mutations arriving mid-flush are simply captured by the next flush rather than lost.
 */
@SculkStable
public class WriteBehindStore<T : Any, ID : Any>(private val repository: SculkRepository<T, ID>, private val idExtractor: (T) -> ID) {
    private val dirty: ConcurrentHashMap<ID, T> = ConcurrentHashMap()

    /** The number of entities currently awaiting a flush. */
    public val pending: Int get() = dirty.size

    /** Marks [entity] dirty, replacing any pending value for the same primary key. */
    public fun markDirty(entity: T) {
        dirty[idExtractor(entity)] = entity
    }

    /** Drops any pending write for [id]. Call after deleting an entity so a stale value isn't re-saved. */
    public fun remove(id: ID) {
        dirty.remove(id)
    }

    /** Drops every pending write without persisting. */
    public fun clear() {
        dirty.clear()
    }

    /**
     * Persists all dirty entities in one batched transaction, then clears them from the buffer.
     *
     * A snapshot is drained first so mutations arriving mid-flush are kept for the next flush. Each
     * drained key is removed only if its value is unchanged, so a newer [markDirty] during the flush
     * survives. If the batch save fails, the drained entities are re-queued (unless a newer value was
     * marked meanwhile) so the data is retried on the next flush.
     */
    public suspend fun flush(): SculkResult<Unit> {
        if (dirty.isEmpty()) return SculkResult.success(Unit)
        val snapshot = HashMap(dirty)
        snapshot.forEach { (id, value) -> dirty.remove(id, value) }
        return when (val result = repository.saveAll(snapshot.values.toList())) {
            is SculkResult.Success -> result

            is SculkResult.Failure -> {
                snapshot.forEach { (id, value) -> dirty.putIfAbsent(id, value) }
                result
            }
        }
    }

    /** Blocking [flush] for shutdown paths where suspending is not possible. */
    public fun flushBlocking(): SculkResult<Unit> = runBlocking { flush() }
}
