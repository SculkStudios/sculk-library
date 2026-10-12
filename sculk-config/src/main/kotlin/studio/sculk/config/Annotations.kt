package studio.sculk.config

import kotlinx.serialization.SerialInfo
import studio.sculk.annotation.SculkStable

/** Written above the key when Sculk generates or extends the file. An operator's own comments win. */
@SculkStable
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Comment(vararg val lines: String)

/** An integer must lie in [min]..[max]. */
@SculkStable
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class Range(val min: Long, val max: Long)

/** A decimal must lie in [min]..[max]. */
@SculkStable
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class DecimalRange(val min: Double, val max: Double)

/** A string must contain something other than whitespace. */
@SculkStable
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class NotBlank
