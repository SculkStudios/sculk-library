package gg.sculk.core.annotation

/**
 * Marks a Sculk Studio API element as experimental.
 *
 * Experimental elements may change or be removed in any minor release
 * without a major version bump. Use with caution in production plugins.
 *
 * Opt in to experimental APIs with [OptIn]:
 * ```kotlin
 * @OptIn(SculkExperimental::class)
 * fun myFunction() { ... }
 * ```
 */
@RequiresOptIn(
    message = "This Sculk Studio API is experimental and may change in future minor releases.",
    level = RequiresOptIn.Level.WARNING,
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
public annotation class SculkExperimental
