package studio.sculk.data.repository

import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import kotlin.reflect.KProperty1

/**
 * Type-safe query builder for [SculkRepository.query].
 *
 * Conditions are expressed against entity properties using infix operators and combined with AND.
 * Property references are resolved to column names by the ORM mapping at execution time.
 *
 * ```kotlin
 * repo.query {
 *     PlayerData::coins greaterThan 1000
 *     PlayerData::name like "Steve%"
 *     orderByDescending(PlayerData::coins)
 *     limit(10)
 * }
 * ```
 */
@SculkStable
public class QueryBuilder<T : Any> {
    @SculkInternal
    public val conditions: MutableList<Condition> = mutableListOf()

    @SculkInternal
    public var orderProperty: String? = null
        private set

    @SculkInternal
    public var orderDescending: Boolean = false
        private set

    @SculkInternal
    public var limitValue: Int? = null
        private set

    /** Equality match. */
    public infix fun <V> KProperty1<T, V>.eq(value: V) {
        conditions += Condition(name, "=", value)
    }

    /** Inequality match. */
    public infix fun <V> KProperty1<T, V>.notEq(value: V) {
        conditions += Condition(name, "<>", value)
    }

    /** Greater-than match. */
    public infix fun <V> KProperty1<T, V>.greaterThan(value: V) {
        conditions += Condition(name, ">", value)
    }

    /** Greater-than-or-equal match. */
    public infix fun <V> KProperty1<T, V>.atLeast(value: V) {
        conditions += Condition(name, ">=", value)
    }

    /** Less-than match. */
    public infix fun <V> KProperty1<T, V>.lessThan(value: V) {
        conditions += Condition(name, "<", value)
    }

    /** Less-than-or-equal match. */
    public infix fun <V> KProperty1<T, V>.atMost(value: V) {
        conditions += Condition(name, "<=", value)
    }

    /** SQL LIKE pattern match. */
    public infix fun KProperty1<T, String>.like(pattern: String) {
        conditions += Condition(name, "LIKE", pattern)
    }

    /** Orders results ascending by [property]. */
    public fun orderBy(property: KProperty1<T, *>) {
        orderProperty = property.name
        orderDescending = false
    }

    /** Orders results descending by [property]. */
    public fun orderByDescending(property: KProperty1<T, *>) {
        orderProperty = property.name
        orderDescending = true
    }

    /** Limits the number of returned rows. */
    public fun limit(count: Int) {
        require(count > 0) { "Query limit must be positive." }
        limitValue = count
    }

    /** A single parsed query condition. The [property] is a Kotlin property name, not a column name. */
    @SculkInternal
    public data class Condition(val property: String, val operator: String, val value: Any?)
}
