package studio.sculk.data

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * An in-memory [SculkRepository] for testing the code that uses one.
 *
 * The point is to test *your* service logic without a database. Sculk's own data layer is tested
 * against real engines instead — a fake that answers queries is not evidence that the SQL is right.
 *
 * ```kotlin
 * val repo = FakeRepository<Profile, UUID>(idOf = { it.id }, columnsOf = { mapOf("coins" to it.coins) })
 * val service = ShopService(repo)
 *
 * service.buy(profile, price = 50)
 *
 * assertEquals(50, repo.stored.single().coins)
 * ```
 *
 * [columnsOf] is what makes [query] work: conditions are evaluated against the map it returns, using
 * the same [Condition] tree the real repository renders to SQL. Supply it only if the code under
 * test issues queries — without it, [query] fails loudly rather than quietly matching everything,
 * because a fake that silently returns the wrong rows is worse than one that refuses.
 */
@SculkStable
public class FakeRepository<T : Any, ID : Any>(
    override val table: String = "fake",
    private val idOf: (T) -> ID,
    private val columnsOf: ((T) -> Map<String, Any?>)? = null,
) : SculkRepository<T, ID> {
    private val rows = ConcurrentHashMap<ID, T>()
    private val writes = AtomicInteger()

    /** Every stored row, in no particular order. */
    public val stored: List<T> get() = rows.values.toList()

    /** How many times a write reached this repository — the assertion for "saved exactly once". */
    public val writeCount: Int get() = writes.get()

    /**
     * Set to fail every subsequent call with this message.
     *
     * A fake that can only succeed leaves the failure branch of the caller untested, which is the
     * branch that runs when the database is down.
     */
    public var failure: String? = null

    /** Seeds rows without counting them as writes. */
    public fun given(vararg values: T) {
        values.forEach { rows[idOf(it)] = it }
    }

    public fun clear() {
        rows.clear()
        writes.set(0)
        failure = null
    }

    private fun <R> guard(block: () -> R): SculkResult<R> = failure?.let { SculkResult.failure(it) } ?: SculkResult.success(block())

    override suspend fun find(id: ID): SculkResult<T?> = guard { rows[id] }

    override suspend fun findAll(): SculkResult<List<T>> = guard { stored }

    override suspend fun query(query: Query.() -> Unit): SculkResult<List<T>> = guard { select(query) }

    override suspend fun findFirst(query: Query.() -> Unit): SculkResult<T?> = guard { select(query).firstOrNull() }

    override suspend fun count(query: Query.() -> Unit): SculkResult<Long> = guard { select(query).size.toLong() }

    override suspend fun exists(id: ID): SculkResult<Boolean> = guard { rows.containsKey(id) }

    override suspend fun save(value: T): SculkResult<Unit> = guard {
        writes.incrementAndGet()
        rows[idOf(value)] = value
    }

    override suspend fun saveAll(values: Collection<T>): SculkResult<Unit> = guard {
        writes.incrementAndGet()
        values.forEach { rows[idOf(it)] = it }
    }

    override suspend fun delete(id: ID): SculkResult<Unit> = guard {
        writes.incrementAndGet()
        rows -= id
    }

    override suspend fun deleteWhere(query: Query.() -> Unit): SculkResult<Int> = guard {
        val doomed = select(query)
        doomed.forEach { rows.remove(idOf(it)) }
        if (doomed.isNotEmpty()) writes.incrementAndGet()
        doomed.size
    }

    override suspend fun topBy(column: String, rows: Int, ascending: Boolean): SculkResult<List<T>> = guard {
        stored
            .sortedWith(comparator(column, ascending))
            .take(rows)
    }

    private fun select(block: Query.() -> Unit): List<T> {
        val query = Query().apply(block)
        val matched = stored.filter { row -> query.condition?.let { matches(it, columns(row)) } ?: true }
        val ordered = query.orderBy.foldRight(matched) { (column, ascending), acc ->
            acc.sortedWith(comparator(column, ascending))
        }
        return ordered
            .drop(query.offset ?: 0)
            .let { rows -> query.limit?.let(rows::take) ?: rows }
    }

    private fun columns(row: T): Map<String, Any?> = (columnsOf ?: error(COLUMNS_REQUIRED))(row)

    private fun comparator(column: String, ascending: Boolean): Comparator<T> {
        val natural = compareBy<T> { columns(it)[column] as? Comparable<*> as? Comparable<Any?> }
        return if (ascending) natural else natural.reversed()
    }

    private fun matches(condition: Condition, columns: Map<String, Any?>): Boolean = when (condition) {
        is Condition.And -> condition.parts.all { matches(it, columns) }
        is Condition.Or -> condition.parts.any { matches(it, columns) }
        is Condition.In -> columns[condition.column] in condition.values
        is Condition.Compare -> compare(columns[condition.column], condition.operator, condition.value)
    }

    private fun compare(actual: Any?, operator: String, expected: Any?): Boolean = when (operator) {
        "=" -> actual == expected

        "<>" -> actual != expected

        // SQL's LIKE, restricted to the wildcards the DSL can produce.
        "LIKE" -> Regex(
            Regex.escape(expected?.toString().orEmpty())
                .replace("%", "\\E.*\\Q")
                .replace("_", "\\E.\\Q"),
            RegexOption.IGNORE_CASE,
        ).matches(actual?.toString().orEmpty())

        else -> {
            val order = ordering(actual, expected)
            when (operator) {
                ">" -> order > 0
                ">=" -> order >= 0
                "<" -> order < 0
                "<=" -> order <= 0
                else -> error("FakeRepository cannot evaluate the operator '$operator'.")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ordering(actual: Any?, expected: Any?): Int {
        // Numbers are compared as numbers regardless of which box they arrived in: a column read
        // back as Long and a query written with an Int are the same value to a database.
        if (actual is Number && expected is Number) return actual.toDouble().compareTo(expected.toDouble())
        val left = actual as? Comparable<Any?> ?: return -1
        return left.compareTo(expected)
    }

    private companion object {
        const val COLUMNS_REQUIRED =
            "FakeRepository was given no columnsOf function, so it cannot evaluate a query. " +
                "Pass columnsOf when the code under test calls query, findFirst, count, deleteWhere or topBy."
    }
}
