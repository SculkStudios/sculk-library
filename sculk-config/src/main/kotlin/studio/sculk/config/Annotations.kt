package studio.sculk.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import studio.sculk.annotation.SculkStable

/**
 * Which file a config class lives in, relative to the plugin's data folder.
 *
 * Bump [revision] when a shipped default changes in a way an existing file cannot be merged into
 * — a value that was wrong, a section that was restructured. On the next load the old file is
 * copied to `<name>.<oldRevision>.bak` and regenerated. The marker is written as a YAML *comment*,
 * so it never appears as a property on the class and never has to be modelled.
 *
 * ```kotlin
 * @Serializable
 * @ConfigFile("combat.yml", revision = 2)
 * data class CombatSettings(val tagSeconds: Int = 30)
 * ```
 */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class ConfigFile(val name: String, val revision: Int = 1)

/**
 * Comment lines written above a key when the file is generated.
 *
 * Works on a property and on the class itself, where it becomes the file header. Comments are
 * matched to keys by path when the file is rendered, so a comment on a nested property lands on
 * the nested key rather than being dropped — which is what happened when comments were emitted by
 * re-walking the top-level constructor parameters only.
 */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Comment(vararg val lines: String)

/** The value must be at least [value]. Violations are warnings, not boot failures. */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Min(val value: Long)

/** The value must be at most [value]. */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Max(val value: Long)

/** The value must not be blank or empty. */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class NotEmpty
