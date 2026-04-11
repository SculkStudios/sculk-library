package gg.sculk.config.annotation

import gg.sculk.core.annotation.SculkStable

/**
 * Validates that a numeric config value is at least [value].
 *
 * Applied to numeric fields in a [@ConfigFile][ConfigFile] data class.
 *
 * Example:
 * ```kotlin
 * @ConfigFile("settings.yml")
 * data class Settings(
 *     @Min(1) val maxHomes: Int = 5,
 * )
 * ```
 */
@SculkStable
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Min(
    val value: Long,
)

/**
 * Validates that a numeric config value is at most [value].
 */
@SculkStable
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Max(
    val value: Long,
)

/**
 * Validates that a string config value is not blank.
 */
@SculkStable
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class NotEmpty
