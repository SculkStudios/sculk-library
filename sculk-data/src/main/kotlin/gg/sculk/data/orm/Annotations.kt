package gg.sculk.data.orm

import gg.sculk.core.annotation.SculkStable

/**
 * Marks a data class as a database entity and optionally overrides the table name.
 * Defaults to the snake_case class name when [name] is empty.
 *
 * ```kotlin
 * @Table("player_data")
 * data class PlayerData(@PrimaryKey val uuid: UUID, val coins: Long = 0)
 * ```
 */
@SculkStable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Table(
    val name: String = "",
)

/**
 * Marks a constructor parameter as the primary key for its entity.
 * Exactly one parameter per entity class must carry this annotation.
 */
@SculkStable
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class PrimaryKey

/**
 * Overrides the SQL column name for a constructor parameter.
 * Defaults to the snake_case property name when omitted.
 */
@SculkStable
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Column(
    val name: String,
)
