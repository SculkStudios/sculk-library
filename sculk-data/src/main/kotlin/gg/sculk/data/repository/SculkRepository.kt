package gg.sculk.data.repository

import gg.sculk.core.SculkResult
import gg.sculk.core.annotation.SculkStable

/**
 * A typed data repository that provides find, save, and delete operations for entity [T]
 * identified by primary key [ID].
 *
 * All methods are **synchronous and blocking** — call them from an async context
 * (e.g. inside a [gg.sculk.core.scheduler.SculkScheduler.runAsync] block).
 *
 * Java users should use the [gg.sculk.data.java.JavaRepository] wrapper which returns
 * `CompletableFuture<SculkResult<T>>` for each operation.
 *
 * ```kotlin
 * scheduler.runAsync {
 *     when (val result = repo.find(player.uniqueId)) {
 *         is SculkResult.Success -> reply("Coins: ${result.value?.coins}")
 *         is SculkResult.Failure -> reply("<red>${result.message}")
 *     }
 * }
 * ```
 */
@SculkStable
public interface SculkRepository<T : Any, ID : Any> {
    /** Finds the entity with the given [id], or null if not found. */
    public fun find(id: ID): SculkResult<T?>

    /** Returns all entities in the table. */
    public fun findAll(): SculkResult<List<T>>

    /**
     * Inserts or updates [entity] based on the primary key.
     * Uses dialect-appropriate upsert SQL.
     */
    public fun save(entity: T): SculkResult<Unit>

    /** Deletes the entity with the given [id]. No-op if not found. */
    public fun delete(id: ID): SculkResult<Unit>

    /** Returns true if an entity with [id] exists. */
    public fun exists(id: ID): SculkResult<Boolean>
}
