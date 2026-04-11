package gg.sculk.core.annotation

/**
 * Marks a Sculk Studio API element as stable.
 *
 * Stable elements are semver-protected. Breaking changes to any element
 * annotated with [SculkStable] require a major version bump.
 *
 * This is the default expectation for all public APIs in Sculk Studio.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class SculkStable
