package gg.sculk.config.annotation

import gg.sculk.core.annotation.SculkStable

/**
 * Emits one or more YAML comment lines directly above a config field when defaults
 * are written to disk by [gg.sculk.config.yaml.YamlMapper].
 *
 * Use this to make generated config files self-documenting for server owners.
 * Multiple lines are supported — separate them with `\n`.
 *
 * ```kotlin
 * @ConfigFile("settings/combat.yml")
 * data class CombatSettings(
 *
 *     @Comment("How long the combat tag lasts in milliseconds.\n1 second = 1000 ms")
 *     val combatTagDuration: Long = 30_000L,
 *
 *     @Comment("Minimum number of hearts a player can have")
 *     val minHearts: Int = 5,
 *
 *     @Comment("Maximum number of hearts a player can have")
 *     val maxHearts: Int = 15,
 * )
 * ```
 *
 * Generated output:
 * ```yaml
 * # How long the combat tag lasts in milliseconds.
 * # 1 second = 1000 ms
 * combat-tag-duration: 30000
 *
 * # Minimum number of hearts a player can have
 * min-hearts: 5
 *
 * # Maximum number of hearts a player can have
 * max-hearts: 15
 * ```
 */
@SculkStable
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Comment(
    val value: String,
)
