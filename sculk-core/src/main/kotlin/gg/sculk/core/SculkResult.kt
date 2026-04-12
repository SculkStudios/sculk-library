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

/**
 * Transforms the success value with [transform], leaving failures unchanged.
 *
 * ```kotlin
 * repo.find(uuid)
 *     .map { it?.coins ?: 0 }
 *     .onSuccess { coins -> player.sendMessage("Coins: $coins") }
 * ```
 */
@SculkStable
public fun <T, R> SculkResult<T>.map(transform: (T) -> R): SculkResult<R> =
    when (this) {
        is SculkResult.Success -> SculkResult.success(transform(value))
        is SculkResult.Failure -> this
    }

/**
 * Chains an operation that itself returns a [SculkResult], propagating failures short-circuit style.
 *
 * ```kotlin
 * repo.find(uuid)
 *     .flatMap { data -> if (data != null) SculkResult.success(data) else SculkResult.failure("not found") }
 *     .onSuccess { data -> player.sendMessage("Hello, ${data.name}") }
 * ```
 */
@SculkStable
public fun <T, R> SculkResult<T>.flatMap(transform: (T) -> SculkResult<R>): SculkResult<R> =
    when (this) {
        is SculkResult.Success -> transform(value)
        is SculkResult.Failure -> this
    }

/**
 * Runs [action] with the value if this is a [SculkResult.Success]. Returns this unchanged.
 *
 * ```kotlin
 * repo.save(data).onSuccess { player.sendMessage("<green>Saved!") }
 * ```
 */
@SculkStable
public fun <T> SculkResult<T>.onSuccess(action: (T) -> Unit): SculkResult<T> {
    if (this is SculkResult.Success) action(value)
    return this
}

/**
 * Runs [action] with the message and cause if this is a [SculkResult.Failure]. Returns this unchanged.
 *
 * ```kotlin
 * repo.find(uuid).onFailure { msg, _ -> logger.warning(msg) }
 * ```
 */
@SculkStable
public fun <T> SculkResult<T>.onFailure(action: (message: String, cause: Throwable?) -> Unit): SculkResult<T> {
    if (this is SculkResult.Failure) action(message, cause)
    return this
}

/**
 * Returns [onSuccess] applied to the value if [SculkResult.Success], or [onFailure] if [SculkResult.Failure].
 *
 * ```kotlin
 * val display = result.fold(
 *     onSuccess = { data -> "<green>${data.name}" },
 *     onFailure = { msg, _ -> "<red>Error: $msg" },
 * )
 * ```
 */
@SculkStable
public fun <T, R> SculkResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (message: String, cause: Throwable?) -> R,
): R =
    when (this) {
        is SculkResult.Success -> onSuccess(value)
        is SculkResult.Failure -> onFailure(message, cause)
    }

/**
 * Recovers from a failure by computing a fallback value.
 *
 * ```kotlin
 * val data = repo.find(uuid).recover { _, _ -> PlayerData.default(uuid) }
 * ```
 */
@SculkStable
public fun <T> SculkResult<T>.recover(onFailure: (message: String, cause: Throwable?) -> T): SculkResult<T> =
    when (this) {
        is SculkResult.Success -> this
        is SculkResult.Failure -> SculkResult.success(onFailure(message, cause))
    }
