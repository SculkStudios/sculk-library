package studio.sculk.data

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * Suspend CRUD over one entity type.
 *
 * Every operation suspends and runs on the IO dispatcher; there is deliberately no blocking
 * variant. The one place that must block — flushing on shutdown — writes `runBlocking` at its own
 * call site rather than being handed a quiet way to do it from anywhere.
 */
@SculkStable
public interface SculkRepository<T : Any, ID : Any> {
    /** The table this repository reads and writes. */
    public val table: String

    public suspend fun find(id: ID): SculkResult<T?>

    public suspend fun findAll(): SculkResult<List<T>>

    /** Every row matching [query]. */
    public suspend fun query(query: Query.() -> Unit): SculkResult<List<T>>

    /** The first row matching [query], or null. Applies a LIMIT rather than filtering in memory. */
    public suspend fun findFirst(query: Query.() -> Unit): SculkResult<T?>

    /** How many rows match, counted by the database rather than by loading them. */
    public suspend fun count(query: Query.() -> Unit = {}): SculkResult<Long>

    public suspend fun exists(id: ID): SculkResult<Boolean>

    public suspend fun save(value: T): SculkResult<Unit>

    public suspend fun saveAll(values: Collection<T>): SculkResult<Unit>

    public suspend fun delete(id: ID): SculkResult<Unit>

    /** Deletes every row matching [query], returning how many went. */
    public suspend fun deleteWhere(query: Query.() -> Unit): SculkResult<Int>

    /**
     * The [rows] highest values of [column], sorted and limited **in SQL**.
     *
     * A leaderboard read. The previous cache-side equivalent loaded the whole table and sorted it
     * in memory, which is survivable at a thousand rows and not at a million.
     */
    public suspend fun topBy(column: String, rows: Int, ascending: Boolean = false): SculkResult<List<T>>
}
