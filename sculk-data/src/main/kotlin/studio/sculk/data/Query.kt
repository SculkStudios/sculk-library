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

/**
 * Renders a condition tree into a WHERE fragment and its ordered parameters.
 *
 * [schema] is what makes a query agree with the rows it is querying. Without it the DSL emitted
 * the Kotlin property name as a column name — wrong the moment `@Column` renamed one — and passed
 * values straight to `setObject`, so a property stored through a serializer was compared in the
 * wrong representation entirely. An `Instant` is written as epoch millis but reaches JDBC as an
 * `Instant`, and the comparison then quietly matches nothing: no error, no rows, no clue.
 */
internal fun Condition.render(dialect: SqlDialect, schema: TableSchema? = null): Pair<String, List<Any?>> = when (this) {
    is Condition.Compare -> {
        val target = schema?.forProperty(column)
        val name = target?.name ?: column
        when {
            // `= NULL` is never true in SQL, which reads as "the query is broken" rather than as
            // "nothing matched".
            value == null && operator == "=" -> "${dialect.quote(name)} IS NULL" to emptyList()

            value == null && operator == "<>" -> "${dialect.quote(name)} IS NOT NULL" to emptyList()

            else -> "${dialect.quote(name)} $operator ?" to listOf(coerce(value, target))
        }
    }

    is Condition.In -> {
        val target = schema?.forProperty(column)
        val name = target?.name ?: column
        // An empty IN () is a syntax error on every engine; the honest translation of "in nothing"
        // is a predicate that matches nothing.
        if (values.isEmpty()) {
            "1 = 0" to emptyList()
        } else {
            "${dialect.quote(name)} IN (${values.joinToString(", ") { "?" }})" to values.map { coerce(it, target) }
        }
    }

    is Condition.And -> combine(parts, "AND", dialect, schema)

    is Condition.Or -> combine(parts, "OR", dialect, schema)
}

/**
 * Converts a query parameter into the representation the column actually holds.
 *
 * The codec writes through the property's serializer, so the stored form of a type like `Instant`
 * or `UUID` is not the object itself. A parameter has to make the same trip or it is comparing a
 * different thing to the one in the row.
 *
 * Only the conversions the shipped serializers perform are handled; anything else is passed
 * through, which is what a plain column has always done.
 */
private fun coerce(value: Any?, column: ColumnSchema?): Any? = when {
    value == null -> null

    // InstantSerializer stores epoch millis. Without this the driver stringifies the Instant and
    // compares text against a BIGINT.
    value is java.time.Instant -> value.toEpochMilli()

    // UuidSerializer stores the canonical string form. Most drivers happen to stringify a UUID the
    // same way, so this was working by luck rather than by design.
    value is java.util.UUID -> value.toString()

    // Enums are stored by name, never ordinal.
    value is Enum<*> -> value.name

    column?.type == SqlType.BOOLEAN && value is Boolean -> value

    else -> value
}

private fun combine(parts: List<Condition>, keyword: String, dialect: SqlDialect, schema: TableSchema?): Pair<String, List<Any?>> {
    if (parts.isEmpty()) return "1 = 1" to emptyList()
    val rendered = parts.map { it.render(dialect, schema) }
    val sql = rendered.joinToString(" $keyword ") { "(${it.first})" }
    return sql to rendered.flatMap { it.second }
}
