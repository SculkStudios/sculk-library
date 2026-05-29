package studio.sculk.data.repository

import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkStable

/**
 * A typed data repository that provides find, save, and delete operations for entity [T]
 * identified by primary key [ID].
 *
 * All methods are **suspend functions** that run their blocking JDBC work off the main thread.
 * Call them from a coroutine — e.g. `sculk.scope.launchAsync { ... }` — and switch back with
 * `withMain { ... }` to touch the Paper API.
 *
 * ```kotlin
 * sculk.scope.launchAsync {
 *     when (val result = repo.find(player.uniqueId)) {
 *         is SculkResult.Success -> withMain { player.sendRichMessage("Coins: ${result.value?.coins}") }
 *         is SculkResult.Failure -> logger.warning(result.message)
 *     }
 * }
 * ```
 */
@SculkStable
public interface SculkRepository<T : Any, ID : Any> {
    /** Finds the entity with the given [id], or null if not found. */
    public suspend fun find(id: ID): SculkResult<T?>

    /** Returns all entities in the table. */
    public suspend fun findAll(): SculkResult<List<T>>

    /**
     * Inserts or updates [entity] based on the primary key.
     * Uses dialect-appropriate upsert SQL.
     */
    public suspend fun save(entity: T): SculkResult<Unit>

    /** Deletes the entity with the given [id]. No-op if not found. */
    public suspend fun delete(id: ID): SculkResult<Unit>

    /** Returns true if an entity with [id] exists. */
    public suspend fun exists(id: ID): SculkResult<Boolean>

    /**
     * Inserts or updates all [entities] in a single batched database operation.
     *
     * Executes inside one transaction — either all succeed or the whole batch is
     * rolled back and a [SculkResult.Failure] is returned.
     */
    public suspend fun saveAll(entities: List<T>): SculkResult<Unit>

    /**
     * Runs a type-safe query against this repository's table.
     *
     * ```kotlin
     * val rich = repo.query { where(PlayerData::coins greaterThan 1000); orderByDescending(PlayerData::coins); limit(10) }
     * ```
     */
    public suspend fun query(block: QueryBuilder<T>.() -> Unit): SculkResult<List<T>>
}
