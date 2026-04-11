package gg.sculk.config.annotation

import gg.sculk.core.annotation.SculkStable

/**
 * Marks a data class as a Sculk Studio configuration file.
 *
 * The [path] is relative to the plugin's data folder.
 *
 * Example:
 * ```kotlin
 * @ConfigFile("settings.yml")
 * data class Settings(
 *     val maxHomes: Int = 5,
 *     val allowFlight: Boolean = false,
 * )
 * ```
 */
@SculkStable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class ConfigFile(
    val path: String,
)
