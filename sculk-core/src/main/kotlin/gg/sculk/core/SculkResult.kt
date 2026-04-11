package gg.sculk.core

import gg.sculk.core.annotation.SculkStable

/**
 * Represents the outcome of a Sculk Studio operation.
 *
 * [SculkResult] is used throughout the framework to return either a [Success]
 * value or a [Failure] with a descriptive message and optional cause.
 *
 * Example:
 * ```kotlin
 * when (val result = repo.find(uuid)) {
 *     is SculkResult.Success -> println(result.value)
 *     is SculkResult.Failure -> logger.warning(result.message)
 * }
 * ```
 */
@SculkStable
public sealed interface SculkResult<out T> {
    /**
     * A successful result carrying a [value].
     */
    @SculkStable
    public data class Success<T>(
        public val value: T,
    ) : SculkResult<T>

    /**
     * A failed result carrying a [message] and an optional [cause].
     */
    @SculkStable
    public data class Failure(
        public val message: String,
        public val cause: Throwable? = null,
    ) : SculkResult<Nothing>

    public companion object {
        /** Wraps [value] in a [Success]. */
        public fun <T> success(value: T): SculkResult<T> = Success(value)

        /** Wraps [message] and optional [cause] in a [Failure]. */
        public fun failure(
            message: String,
            cause: Throwable? = null,
        ): SculkResult<Nothing> = Failure(message, cause)
    }
}

/** Returns the value if this is a [SculkResult.Success], or null otherwise. */
@SculkStable
public fun <T> SculkResult<T>.getOrNull(): T? = (this as? SculkResult.Success)?.value

/** Returns the value if this is a [SculkResult.Success], or [default] otherwise. */
@SculkStable
public fun <T> SculkResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

/** Returns true if this is a [SculkResult.Success]. */
@SculkStable
public val SculkResult<*>.isSuccess: Boolean get() = this is SculkResult.Success

/** Returns true if this is a [SculkResult.Failure]. */
@SculkStable
public val SculkResult<*>.isFailure: Boolean get() = this is SculkResult.Failure
