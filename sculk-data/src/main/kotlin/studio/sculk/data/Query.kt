package studio.sculk.data

import studio.sculk.annotation.SculkStable
import kotlin.reflect.KProperty1

/** A predicate over one entity's columns. */
@SculkStable
public sealed interface Condition {
    @SculkStable
    public data class Compare(val column: String, val operator: String, val value: Any?) : Condition

    @SculkStable
    public data class In(val column: String, val values: List<Any?>) : Condition

    @SculkStable
    public data class And(val parts: List<Condition>) : Condition

    @SculkStable
    public data class Or(val parts: List<Condition>) : Condition
}

/**
 * A read, expressed against columns rather than as SQL.
 *
 * The previous builder was AND-only with a single ORDER BY and no OFFSET, so anything else meant
 * `findAll().filter { }` — pulling the table into memory to discard most of it, which is the exact
 * failure this type exists to prevent.
 *
 * ```kotlin
 * repo.query {
 *     PlayerData::coins greaterThan 1000
 *     any {
 *         PlayerData::rank eq "vip"
 *         PlayerData::rank eq "mvp"
 *     }
 *     orderByDescending(PlayerData::coins)
 *     take(10)
 * }
 * ```
 */
@SculkStable
public class Query internal constructor() {
    private val conditions = mutableListOf<Condition>()
    private val ordering = mutableListOf<Pair<String, Boolean>>()

    internal var limit: Int? = null
        private set

    internal var offset: Int? = null
        private set

    internal val condition: Condition? get() = when {
        conditions.isEmpty() -> null
        conditions.size == 1 -> conditions.single()
        else -> Condition.And(conditions.toList())
    }

    internal val orderBy: List<Pair<String, Boolean>> get() = ordering.toList()

    // Typed against the property so a renamed field is a compile error rather than a query that
    // silently matches nothing.
    @SculkStable
    public infix fun <T, V> KProperty1<T, V>.eq(value: V?): Unit = add(Condition.Compare(name, "=", value))

    @SculkStable
    public infix fun <T, V> KProperty1<T, V>.notEq(value: V?): Unit = add(Condition.Compare(name, "<>", value))

    @SculkStable
    public infix fun <T, V : Comparable<V>> KProperty1<T, V>.greaterThan(value: V): Unit = add(Condition.Compare(name, ">", value))

    @SculkStable
    public infix fun <T, V : Comparable<V>> KProperty1<T, V>.atLeast(value: V): Unit = add(Condition.Compare(name, ">=", value))

    @SculkStable
    public infix fun <T, V : Comparable<V>> KProperty1<T, V>.lessThan(value: V): Unit = add(Condition.Compare(name, "<", value))

    @SculkStable
    public infix fun <T, V : Comparable<V>> KProperty1<T, V>.atMost(value: V): Unit = add(Condition.Compare(name, "<=", value))

    @SculkStable
    public infix fun <T> KProperty1<T, String?>.like(pattern: String): Unit = add(Condition.Compare(name, "LIKE", pattern))

    @SculkStable
    public infix fun <T, V> KProperty1<T, V>.isIn(values: Collection<V>): Unit = add(Condition.In(name, values.toList()))

    /** Groups nested conditions with OR. */
    @SculkStable
    public fun any(block: Query.() -> Unit) {
        val nested = Query().apply(block)
        nested.condition?.let { grouped ->
            add(if (grouped is Condition.And) Condition.Or(grouped.parts) else grouped)
        }
    }

    @SculkStable
    public fun <T, V> orderBy(property: KProperty1<T, V>) {
        ordering += property.name to true
    }

    @SculkStable
    public fun <T, V> orderByDescending(property: KProperty1<T, V>) {
        ordering += property.name to false
    }

    /** Caps the number of rows returned. */
    @SculkStable
    public fun take(rows: Int) {
        limit = rows
    }

    /** Skips [rows] before returning any. */
    @SculkStable
    public fun skip(rows: Int) {
        offset = rows
    }

    private fun add(condition: Condition) {
        conditions += condition
    }
}

/** Renders a condition tree into a WHERE fragment and its ordered parameters. */
internal fun Condition.render(dialect: SqlDialect): Pair<String, List<Any?>> = when (this) {
    is Condition.Compare -> when {
        // `= NULL` is never true in SQL, which reads as "the query is broken" rather than as
        // "nothing matched".
        value == null && operator == "=" -> "${dialect.quote(column)} IS NULL" to emptyList()

        value == null && operator == "<>" -> "${dialect.quote(column)} IS NOT NULL" to emptyList()

        else -> "${dialect.quote(column)} $operator ?" to listOf(value)
    }

    is Condition.In ->
        // An empty IN () is a syntax error on every engine; the honest translation of "in nothing"
        // is a predicate that matches nothing.
        if (values.isEmpty()) {
            "1 = 0" to emptyList()
        } else {
            "${dialect.quote(column)} IN (${values.joinToString(", ") { "?" }})" to values
        }

    is Condition.And -> combine(parts, "AND", dialect)

    is Condition.Or -> combine(parts, "OR", dialect)
}

private fun combine(parts: List<Condition>, keyword: String, dialect: SqlDialect): Pair<String, List<Any?>> {
    if (parts.isEmpty()) return "1 = 1" to emptyList()
    val rendered = parts.map { it.render(dialect) }
    val sql = rendered.joinToString(" $keyword ") { "(${it.first})" }
    return sql to rendered.flatMap { it.second }
}
