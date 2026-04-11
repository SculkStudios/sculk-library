package gg.sculk.core.annotation

/**
 * Marks a Sculk Studio API element as internal to the framework.
 *
 * Internal elements are not covered by semver guarantees and must not be
 * used by plugin code. They may change, move, or be removed at any time.
 *
 * Elements annotated with [SculkInternal] are public only because Kotlin
 * requires `public` for cross-module access within the framework itself.
 */
@RequiresOptIn(
    message = "This is an internal Sculk Studio API. It must not be used in plugin code.",
    level = RequiresOptIn.Level.ERROR,
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class SculkInternal
